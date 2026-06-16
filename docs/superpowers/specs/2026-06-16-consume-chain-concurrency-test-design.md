# 消费链真实两线程并发测试设计

## 背景

`docs/code-quality-audit-status.md` 将「缺真实两线程并发测试」列为当前最高优先级遗留项。现有并发相关测试主要验证仓储方法、注解、查询形状和分配器纯逻辑，缺少一个真正由两个线程同时进入 Spring 事务、竞争同一批开放消费链的回归测试。

当前生产路径已经具备两层锁：

- `TransactionMountMsgService.saveTransactionMountMsg` 通过 `TransactionRecordMsgRepository.findByIdForUpdate` 锁定被挂载的交易记录，防止同一交易记录被重复挂载。
- `ConsumeChainAllocationService.saveConsumeChain` 通过 `ConsumeChainRepository.lockOpenChainsForAllocation` 的 PostgreSQL `for update` 锁定待分配的开放消费链。

审计项关注的是第二层：不同交易记录的挂载请求并发竞争同一开放消费链时，是否会重复消费同一链段或产生金额不守恒。

本轮选择「真实测试 + 必要时最小生产修复」方案。

## 目标

- 新增一个真实 PostgreSQL/Testcontainers 支撑的两线程并发集成测试。
- 两个线程必须同时进入真实 Spring 事务，而不是只用 Mockito 或反射检查注解。
- 并发请求必须使用不同 `TransactionRecordMsg`，避免被 `findByIdForUpdate` 的同一记录锁提前串行化。
- 两个请求必须竞争同一条开放消费链，覆盖 `lockOpenChainsForAllocation(... for update)` 的实际数据库行为。
- 测试完成后验证消费链金额守恒、边记录归属合理、同一开放链不会被两个挂载重复完整消费。
- 如果测试暴露竞态，只做最小生产修复，优先限定在事务边界、锁 SQL 或必要唯一约束。

## 非目标

- 不重写消费链分配算法。
- 不引入分布式锁、队列或全局串行化机制。
- 不处理实体 `@Version`、`equals/hashCode`、`byte[] @Id` 等 Low/Info 防御项。
- 不扩大到 HTTP 端到端并发压测。
- 不调整 API 错误契约、rawBytes 输出策略或结构性 Service 模板化。

## 测试架构

新增测试建议放在 `src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java`，继承 `NmsciIntegrationTestBase`：

- 复用真实 Spring Boot 上下文、PostgreSQL Testcontainers、Flyway schema 和每测清库。
- 注入 `TransactionMountMsgService`，直接调用 service 方法，让 `@Transactional` 代理创建真实事务。
- 注入 `FlowNodeRegisterMsgRepository`、`TransactionRecordMsgRepository`、`TransactionMountMsgRepository`、`ConsumeChainRepository`、`ConsumeChainEdgeRepository` 用于断言。
- 使用 `ProtocolMessageBuilder` 生成合法协议字节，再用现有 converter 转为实体，避免绕过签名、PoW、rawBytes 形状和中心联署前置逻辑。

测试不使用 `MockMvc` 作为主入口。理由是本轮目标是消费链分配事务竞争，不是 HTTP 编解码；service 层更聚焦，失败时能更直接定位到锁和持久化逻辑。

## 并发场景

测试数据使用三个确定性流转节点：

- A：初始来源节点。
- B：共享开放链尾节点，也是两个并发挂载的来源节点。
- C：两个并发交易记录的目标节点。

准备阶段：

1. 注册并授权 A、B、C 三个流转节点。
2. 保存一笔交易记录 `R0`，其流转节点为 B，金额为 `1000`。
3. 保存挂载 `M0`，由 A 挂载 `R0`。这会形成一条开放链 `A -> B`，金额 `1000`，尾节点为 B。
4. 保存两笔交易记录 `R1`、`R2`，其流转节点均为 C，金额均为 `700`。

并发阶段：

1. 构造两个挂载实体 `M1`、`M2`，均由 B 作为挂载流转节点，分别挂载 `R1`、`R2`。
2. 用 `ExecutorService` 创建两个工作线程。
3. 用 `CountDownLatch ready/start/done` 确保两个线程都准备好后同时调用 `TransactionMountMsgService.saveTransactionMountMsg`。
4. 收集每个线程的返回值和异常；测试必须等待两个线程结束，并在 `finally` 中关闭线程池。

断言阶段：

- 两个挂载均保存成功，`transaction_mount_msgs` 对应记录存在。
- `consume_chain_edges` 中 `M1` 与 `M2` 各自只有本次挂载产生的边。
- `M1` 与 `M2` 各自关联的边金额合计均等于各自交易记录金额 `700`。
- 所有消费链金额均为正数；最终以 C 为尾节点的链金额合计为 `1400`，其中 `1000` 来自原 `A -> B` 开放链被两次挂载逐步推进，`400` 是第二次挂载在 B 处新增的不足部分。
- 不允许两个挂载都把同一条 `A -> B` 金额 `1000` 当成完整可用前缀；原开放链要么被一个线程拆分后被另一个线程消费剩余部分，要么被一个线程完整推进后另一个线程只能创建新的 B→C 不足部分。
- 最终不应存在尾节点仍为 B 的开放链金额，因为两个 `700` 挂载合计已经覆盖原 `1000` 的 B 端入链。

具体断言会以持久化结果为准，而不是依赖线程完成顺序。测试允许两个线程任意先后提交，但不允许违反金额守恒和重复消费。

## 生产修复边界

如果新增测试直接通过，本轮不改生产代码。

如果测试失败，只允许以下最小修复方向：

- 调整 `lockOpenChainsForAllocation` 的锁定 SQL，使候选链选择和 `for update` 锁定在 PostgreSQL 下真正串行化同一批开放链。
- 收紧事务边界，确保消费链读取、分配和保存处于同一事务内。
- 增加必要的数据库约束，防止同一挂载或同一记录重复建立边，但不改变正常分配模型。

不接受的修复：

- 用 JVM 全局 `synchronized` 或进程内锁替代数据库并发控制。
- 将所有挂载请求全局串行化。
- 重写 `ConsumeChainAllocator` 的业务分配语义。

## 测试策略

按 TDD 执行：

1. 先新增并发集成测试，运行 focused verify/test，确认测试能真实执行两线程场景。
2. 如果红灯，分析失败是否为真实竞态；只做最小生产修复。
3. focused 测试通过后运行全量 `mvnw test` 和 `mvnw verify`。
4. 更新 `docs/code-quality-audit-status.md`，把「缺真实两线程并发测试」标记为已修复，并从下一轮建议优先级移除。

验收命令：

```powershell
.\mvnw.cmd -Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest verify
.\mvnw.cmd test
.\mvnw.cmd verify
```

`verify` 需要 Docker/Testcontainers 可用。若 Docker 或镜像网络失败，应记录精确失败原因，不把该项误报为代码失败。

## 风险与权衡

- 真实并发测试天然可能比普通单元测试慢。为降低波动，只跑两个线程、一个明确竞争场景，不做压力测试。
- 使用 service 层入口牺牲了 HTTP 编解码覆盖，但保留了真实事务、锁、签名、PoW、中心联署和持久化路径，足以覆盖审计关注点。
- 并发完成顺序不可预测，因此断言必须围绕最终不变量，而不是固定链 ID 或线程顺序。
- 如果 PostgreSQL 锁行为已正确，测试会直接绿灯；本轮仍有价值，因为它把之前只靠注解和 SQL 形状推断的并发保障变成可回归验证。
