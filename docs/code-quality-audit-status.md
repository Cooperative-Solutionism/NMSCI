# NMSCI 代码质量审计 — 修复状态清单

- **核验日期**：2026-06-16
- **核验基线**：本清单提交所在 HEAD（工作树干净）；本轮消费链接口契约修复包含 `e9d5f7e`、`7f60626`、`a3d9bcd`、`e749633` 及后续文档校正提交。
- **审计来源**：多智能体并行审计（11 维度 / 100 条保留发现 / 6 条对抗验证证伪）
- **codex 修复范围**：见 `docs/superpowers/specs/2026-06-15-quality-fixes-design.md`（设计）与 `docs/superpowers/plans/2026-06-15-quality-fixes.md`（实施计划），以及 `docs/superpowers/specs/2026-06-16-source-hash-build-tool-design.md` / `docs/superpowers/plans/2026-06-16-source-hash-build-tool.md`（源码哈希构建工具硬化）、`docs/superpowers/specs/2026-06-16-secp256k1-negative-tests-design.md` / `docs/superpowers/plans/2026-06-16-secp256k1-negative-tests.md`（Secp256k1 原语负路径与边界守卫）。
- **验证手段**：逐条比对当前已提交代码 + focused surefire `CalcSourceCodeZipHashContractTest`（9 tests）/ `Secp256k1EncryptUtilTest`（11 tests）+ `mvnw package` 源码包生成 + full `mvnw test`（161 tests）+ full `mvnw verify`（surefire 161 + failsafe 34 tests），Maven 全绿；Docker build-stage 复验因 Docker Hub token/network 故障未完成（见 §6）。

## 图例

| 标记 | 含义 |
|:---:|------|
| ✅ | 已修复并核验 |
| ⚖️ | 以决策方式收口（如改文档而非改代码）—— 有意为之，非遗漏 |
| ⏸️ | 有意延后（codex 设计文档明确的非目标）|
| ⚠️ | 修复后仍残留的小问题 / 新引入的关注点 |
| 🔁 | 上一轮审计的假阳性 / 陈旧描述，已更正 |

---

## 1. 总览

| 指标 | 数值 |
|------|------|
| 审计保留发现 | 100（High 2 / Medium 19 / Low 71 / Info 8） |
| 综合评分（审计时） | 约 65 / 100 |
| 本轮已修复（含决策收口） | High 1（唯一真 High）+ Medium 15 + 多个 Low/Info |
| 本轮有意延后 | 大型重构、并发测试、多数 Low/Info |
| 单元测试 | ✅ 通过（surefire / mvnw test，161 tests） |
| 集成测试 | ✅ 通过（failsafe / mvnw verify，34 tests） |

**维度评分卡（审计时基线，供下一轮对比）：** Correctness 7 · Concurrency 6 · Security 7 · Persistence 6 · Performance 7 · Architecture 6 · Maintainability 6 · Error Handling 6 · API/REST 7 · Test 6 · Config/Build/Ops 7。

---

## 2. 已修复（✅ / ⚖️）

### 2.1 High

| # | 问题 | 位置 | 状态 | 实现 / commit |
|:--:|------|------|:--:|------|
| H1 | 业务 Service 方法里直接 `System.exit(0)`，从请求线程粗暴杀 JVM | `service/CentralPubkeyLockedMsgService.java` | ✅ | 改事件驱动优雅停机：新增 `CentralPubkeyLockShutdownService` 接口 + `SpringCentralPubkeyLockShutdownService`（`AtomicBoolean` 去重 + 命名非守护线程 `SpringApplication.exit`）；配 `server.shutdown=graceful`。commit `7ab12ae` |

### 2.2 Medium

| # | 问题 | 位置 | 状态 | 实现 / commit |
|:--:|------|------|:--:|------|
| M1 | `CentralPubkeyLockedMsg` 错型（`ConfirmableMessage`）→ 手抄整套冻结字节布局/low-S/签名 | `model/CentralPubkeyLockedMsg.java`、`service/CentralPubkeyLockedMsgService.java` | ✅ | 改 `implements CentrallySignedMessage`；走共享 `ProtocolRawBytesBuilder.centralPubkeyLockedVerifyData` + `SignatureValidator` + `CentralSignatureService.signAndPopulate`；删 shim 改 `TransactionTemplate`。commit `7ab12ae` |
| M2 | 创世前 NPE：`findTopByOrderByHeightDesc()` 未判空（难度服务） | `protocol/BlockDifficultyService.java` | ✅ | 判空抛 `ConflictException("区块链尚未初始化…")`。commit `7d89f43` |
| M3 | 创世前 NPE（流转节点注册路径） | `service/FlowNodeRegisterMsgService.java:65` | ✅ | 同上，判空抛 `ConflictException`。commit `7d89f43` |
| M4 | 中心公钥与私钥未校验匹配 → 静默用错密钥联署 | `config/properties/NmsciProperties.java` | ✅ | `@AssertTrue isKeyPairMatched()`：由私钥推导压缩公钥逐字节比对，启动即失败；形状非法时跳过交由既有校验。commit `9d4cd64` / `0ce314f` |
| M5 | 调度出块无错误处理，失败被默认 log-and-suppress 静默吞掉 | `task/GenerateBlockTask.java` | ✅ | 顶层 try/catch + 上下文错误日志；`isFirstTimeRun` 仅在成功排空后置 false（失败可重试）。Micrometer 指标显式延后。commit `7d89f43` |
| M6 | `bytesToHex` 用 `String.format` 在热序列化路径 | `util/ByteArrayUtil.java:191` | ✅ | 换 `HexFormat.of().formatHex` + null 守卫。commit `78875f0` |
| M7 | Flyway `baseline-version=1` 与唯一迁移 `V1` 碰撞，可静默跳过建表 | `resources/application.properties:4` | ✅ | 改 `=0` + 运维注释。commit `0663d5e` |
| M8 | BouncyCastle 1.78 处于攻击面的安全公告 | `pom.xml:19` | ✅ | 升 `1.84` 并排除 bitcoinj 传递的 `bcprov-jdk15to18`（避免双 bcprov）。crypto 单测全绿。commit `0663d5e` |
| M9 | 冻结 Service 的 save 逻辑（内联加密 + System.exit）无测试 | `test/.../CentralPubkeyLockedMsgServiceTest.java` | ✅ | 新增（+137 行）+ `GenerateBlockTaskTest`、`NmsciPropertiesValidationTest` 等。commit `2de01b5` / `f766a75` |
| M10 | crypto/IO 粗包装成 `RuntimeException`，坏签名 → 500（冻结路径） | `service/CentralPubkeyLockedMsgService.java` | ✅* | 冻结路径已改走 `SignatureValidator`（输入错误 → 400）。*非冻结路径的全局 IAE→400 契约未动（见 §3）。commit `7ab12ae` |
| M11 | `rawBytes` 序列化输出与 API 文档矛盾 | 6 个消息实体 + `docs/API.md` | ⚖️ | **决策保留输出**（普通消息 rawBytes 长度有限，作为调试/追溯字段）；同步更新 API.md §1.4/§10.2。commit `2de01b5` |

### 2.3 Low / Info（已顺带修复）

| 问题 | 位置 | 状态 | 实现 |
|------|------|:--:|------|
| `PoWUtil` 指数无界 / 负指数崩溃 | `util/PoWUtil.java` | ✅ | exponent 3..32 + mantissa>0 校验。`78875f0` |
| 低-S 校验对非定长签名无防御 | `protocol/SignatureValidator.java` | ✅ | 在 `validateLowS` 入口加 `length==64` 守卫；原语 `Secp256k1EncryptUtil.isNotLowS` 后续已补稳定输入守卫。`78875f0` / `f84fc96` |
| `bytesToHex` 无 null 守卫（与同类方法不一致） | `util/ByteArrayUtil.java` | ✅ | 已加 null 守卫。`78875f0` |
| 无 `ddl-auto=validate`，实体/DDL 漂移不报 | `resources/application.properties` | ✅ | 加 `validate`；`mvnw verify` 已确认 Testcontainers 启动与 schema validate 通过。`0663d5e` |
| Dockerfile 承诺优雅停机但未配 `server.shutdown=graceful` | `resources/application.properties` | ✅ | 已加。`0663d5e` |
| 事务 shim（`CentralPubkeyLockedMsgPersistenceService`）仅为绕过事务存在 | （已删除） | ✅ | 删除该类，改 `TransactionTemplate`。`7ab12ae` |

### 2.4 本轮新增修复（2026-06-16）
- ✅ **`/consume-chains/edges` 分页修复**：已改为 `SliceResponseDTO<ConsumeChainEdge>`，新增 `page`/`size`，复用 `PageRequestUtil` 200 上限，native query 使用 `size+1`/`offset`。
- ✅ **查询期 not-found 语义修复**：未冻结/未授权/不存在改 `NotFoundException` → 404；格式错误、缺参、非法 pubkey 长度仍 400。
- ✅ **源码哈希构建链路硬化**：源码包文件集改由 `git ls-files -z` 枚举 git 跟踪文件，内容仍读取当前工作树；历史生成物 `source_code_v*.zip` 明确排除；git/zip/hash/properties 任一阶段失败均非零退出，Maven antrun 配置 `failonerror=true`；Docker build context 有意保留 `.git` 以支持容器内 `git ls-files`；路径加固拒绝符号链接及符号链接祖先，并校验 `nmsci.block-version` 必须为正整数。commits `3842af1` / `bd36112` / `bd3b8c5` / `5b06bd6`。
- ✅ **Secp256k1 原语负路径测试与边界守卫**：补充错误公钥、篡改数据、high-S、非 64 字节 RS、畸形 DER、非法 `r/s`、非法压缩公钥、非法原始私钥等 primitive 单元测试；`derToRs` 拒绝非法 ASN.1/标量，`isNotLowS`/`rsToDer`/密钥转换方法增加稳定输入守卫。

---

## 3. 有意延后（⏸️，codex 设计文档明确非目标）

> 这些**不是未完成**，是这一轮刻意未纳入。按建议优先级从高到低。

### 较高价值（建议下一轮优先）
- **缺真实两线程并发测试**（Medium，Test）：双挂载 / 并发分配仅靠反射断言注解；建议补 `ExecutorService` + `CountDownLatch` 竞争测试。

### 结构性重构（成本较高）
- 5–6 个写 Service 重复约 30 行管线 → 模板方法 / 管线抽象（Low）。
- `hexToBytesOrNull` ×6 控制器 + `notBlank/uuid/pubkey` ×2 重复 → 抽共享工具（Low）。
- 魔法值 `33`/`64` 散落 17+ 处 → 共享协议常量（Low）。
- `ConsumeChainQueryService` ~8–10 个近重复 `getConsumeChainBy*` 方法（Low）。
- `BlockAssembler.findMessages` 6 分支 if/else 类型派发 + `isInBlock` 副作用（Low）。
- 写控制器绕过 `ProtocolMessageCodec`，`@ByteArraySize` 长度第三处硬编码（Low）。
- repository 反向依赖 `block/dto/protocol` 投影类型（Low）。
- `WebMvcConfig extends WebMvcConfigurationSupport` 关闭了 Boot MVC 自动配置（Low）。

### 错误契约 / API 打磨（Low，整体一致性）
- `IllegalArgumentException` 全局映射 400（误标内部故障 + 泄露 JDK/解析器原文）。
- `ResponseResult.failure` 把错误详情放类型化 `data` 而非 `message`。
- 写端点未声明 `consumes`（文档要求 octet-stream 未强制）。
- 自定义 Jackson 序列化器无 null 守卫；响应 DTO 非标准 boolean getter；POST 返回 200 而非 201、重试 409 非幂等。
- 搜索端点 pubkey 无长度校验（静默返回空）。

### JPA / 并发 / 性能（Low/Info，纵深防御与优化）
- 实体无 `equals/hashCode`、无 `@Version`；`LoopMarker` 改 id 比较（**见 §5：非活跃 bug，属防御性加固**）。
- `byte[]` 作 `@Id` 的哈希集合脚枪；`BlockInfo.rawBytes`（~1MB）急加载；`ConsumeChainQueryService` 读方法未 `@Transactional(readOnly=true)`。
- PoW + 双 ECDSA 验签在持有悲观锁的事务窗口内；`findDistinctByNode` 相关子查询 EXISTS；`verifySignature` 重复编解码公钥；`BlockAssembler` 分型查询 + 集合未预分配。
- `isFirstTimeRun` 非 volatile（当前单线程调度器下安全）；advisory lock 不变量未文档化。
- **区块体/Merkle 顺序非确定**（`findPayloadByIdIn` 无 `ORDER BY`）—— Persistence 维度评估提及，建议确认是否需稳定排序。

### 配置 / 测试基建（Low/Info）
- docker `api.version=1.40` 三处重复；`calcFileHash` 逐字节读;版本常量散落;空 `<requiresUnpack/>`。
- 源码字符串式 / DDL 子串匹配「契约」测试脆弱;`ProtocolMessageCodec` 零测试;手搓 Testcontainer 生命周期;失败安全网 `*Test` vs `*IT` 约定;Gatling 模拟无 CI 回归价值。
- Merkle CVE-2012-2459 重复尾延展性（Info，应文档化）;`ConsumeChainAllocator` 顺序 if 改 if/else（Low，结构防御）。

---

## 4. 修复后残留关注点（⚠️）

| # | 关注点 | 说明 / 建议 |
|:--:|------|------|
| R1 | 创世前用 `ConflictException`→409 | 语义上 503（未就绪）更贴切;复用既有类型可接受,javadoc 已放宽为「lifecycle state」 |
| R2 | 冻结仍是「保存(TransactionTemplate)→排空(独立事务)→停机」三段 | 设计明确接受的权衡。残留风险:排空在冻结记录提交后抛异常 → 留下「已冻结但排空未完成、未触发停机、仍在服务」中间态。建议 drain 用 try/finally 或幂等可重入,失败时也明确告警 |

---

## 5. 更正：上一轮审计的陈旧/假阳性（🔁）

- **`LoopMarker` 引用相等被判 High「生产环境环路漏检」→ 实为非活跃 bug。** 经直接追踪调用链确认：`ConsumeChainAllocationService` 先把 `source`/`target` 载入持久化上下文,之后加载链;链 hydrate 时 Hibernate 经 `createProxyIfNecessary` **先查 PC**,把 LAZY `start`/`end` 解析为同一受管实例 → 引用相等成立,环路正确标记。底层脆弱性（依赖加载时序 + 测试用同一对象掩盖）真实存在,但**不是污染回流率指标的活跃 bug**,降为防御性加固项（见 §3）。
- **冻结 `System.exit`、创世 NPE、密钥对一致性、原语边界、BC 版本、Flyway 等项**：上一轮后台审计的描述与 **2026-06-15 之前的旧代码**吻合（甚至描述了已删除的 shim 与 line 134 的 `System.exit`），即读到的是修复前快照,对这些项过度报告。**以本清单对当前已提交代码的直接核验为准。**

---

## 6. 验证记录与待办

- ✅ focused surefire 通过：`.\mvnw.cmd -Dtest=CalcSourceCodeZipHashContractTest test`，9 tests passed（Failures 0 / Errors 0 / Skipped 0）。
- ✅ focused surefire 通过：`.\mvnw.cmd -Dtest=Secp256k1EncryptUtilTest test`，11 tests passed（Failures 0 / Errors 0 / Skipped 0）。
- ✅ package 通过：`.\mvnw.cmd -DskipTests package` BUILD SUCCESS；生成 `target/classes/static/source_code_v1.zip`，并写入非零 `nmsci.source-code-zip-hash`。由于本审计文档本身是 `git ls-files` 纳入的跟踪源码，任何跟踪源码/文档变更都会改变源码包哈希；因此此处记录非零生成事实，不记录固定哈希值。
- ✅ full `mvnw test`（surefire 单元测试）通过：161 tests passed（Failures 0 / Errors 0 / Skipped 0）；覆盖密钥对校验、创世异常、调度失败、低-S/PoW/HexFormat 边界、Secp256k1 原语负路径、冻结流程停机与事务边界。
- ✅ full **`mvnw verify`（surefire + failsafe 集成测试，需 Docker）通过。** 覆盖 JPA/Flyway 启动期 schema validate 与协议生命周期端到端行为；本轮完整 verify 为 surefire 161 个、failsafe 34 个用例通过（均 Failures 0 / Errors 0 / Skipped 0）。
- ⚠️ Docker build-stage 复验未完成：`docker build --target build -t nmsci-source-hash-build-test .` 在进入 build stage 前拉取 Docker Hub token 失败，当前完整失败行为的精确摘录如下。该项仅阻塞 Docker 镜像构建阶段复验，不影响上述 Maven 本地验证结论。

```text
ERROR: failed to solve: failed to fetch anonymous token: Get "https://auth.docker.io/token?scope=repository%3Adocker%2Fdockerfile%3Apull&service=registry.docker.io": dial tcp 199.96.62.21:443: connectex: A connection attempt failed because the connected party did not properly respond after a period of time, or established connection failed because connected host has failed to respond.
```

---

## 7. 下一轮建议优先级

1. 双挂载 / 并发分配的真实两线程测试。
2. 其余结构性重构（写 Service 模板化、常量化、去重）按团队节奏推进。
