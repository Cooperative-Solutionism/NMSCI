# NMSCI 代码质量修复设计

日期：2026-06-15

## 背景

本设计根据 `D:\Data\code\circular-trading-system\temp\NMSCI\problem.md` 的代码质量分析报告制定。当前项目是 Java 21 / Spring Boot 3.5.3 后端系统，问题主要集中在中心公钥冻结流程、启动与配置防御、协议原语边界校验、创世块尚未初始化时的错误处理，以及少量构建和依赖风险。

## 目标

本轮修复选择“快赢 + 唯一 High 问题止血”的范围：

- 移除冻结流程中的 `System.exit(0)`，避免 HTTP 请求线程直接终止 JVM。
- 将 `CentralPubkeyLockedMsg` 纳入现有中心签名协议抽象，减少手工拼接和签名分叉。
- 删除或停止使用仅为绕过事务边界存在的 `CentralPubkeyLockedMsgPersistenceService`。
- 增加中心公钥和私钥匹配校验，配置错误时启动失败。
- 处理创世块尚未存在时的 NPE，返回明确业务错误。
- 加固 `ByteArrayUtil.bytesToHex`、PoW nBits 解析、低-S 签名校验等原语边界。
- 调整 Flyway baseline、JPA schema validate、优雅停机配置。
- 升级 BouncyCastle 到兼容的补丁版本。
- 为上述行为补充 focused tests。
- 所有 git 提交信息使用中文，提交作者使用 `GPT5.5XH`。

## 非目标

- 不隐藏普通消息实体的 `rawBytes`。普通消息 rawBytes 长度有限，本轮保留现有 API 输出行为。
- 不实现 `/consume-chains/edges` 分页。
- 不重塑全局错误响应结构。
- 不批量重构所有写 Service 管线。
- 不新增实体 `equals/hashCode`、`@Version` 或真实两线程并发测试。
- 不处理报告中所有 Low / Info 项。

## 方案选择

采用一次性完成“快赢 + 冻结流程止血”的方案。

该方案优先处理唯一 High 风险和低成本高收益问题，避免把分页、全局错误契约、并发测试体系等中等或大范围改造混入同一轮提交。相比只做配置和工具方法，它能消除最危险的 `System.exit(0)`；相比全量中等改造，它的行为面和测试面更可控。

## 冻结流程设计

`CentralPubkeyLockedMsgService` 保持负责冻结消息的业务校验、签名填充、落库和排空未装块消息，但不再直接调用 `System.exit(0)`。

冻结消息保存流程调整为：

1. 校验消息类型、ID 唯一性、中心公钥未冻结、消息中的中心公钥等于当前配置中心公钥。
2. 使用 `ProtocolRawBytesBuilder.centralPubkeyLockedVerifyData(...)` 构建预签名验证数据。
3. 使用 `SignatureValidator` 校验 64 字节预签名为 low-S，并验证预签名确实由当前中心公钥产生。
4. 让 `CentralPubkeyLockedMsg` 实现 `CentrallySignedMessage`。
5. 使用 `CentralSignatureService.signAndPopulate(...)` 统一填充 `confirmTimestamp`、`centralSignature`、`rawBytes`、`txid`。
6. 使用 `TransactionTemplate` 在一个明确事务内保存冻结消息和对应 `MsgAbstract`。
7. 保存事务提交后，调用现有 `BlockChainService.generateBlockUntilNoNotInBlockMsgs()` 排空未装块消息。该调用不加入冻结消息保存事务。
8. 调用可测试的停机请求服务，请求线程不再调用 `System.exit(0)`。

停机请求设计：

- 新增 `CentralPubkeyLockShutdownService` 接口，`CentralPubkeyLockedMsgService` 只依赖该接口并在流程完成后调用 `requestShutdown()`。
- 生产实现使用 `AtomicBoolean` 防止重复停机请求。
- 生产实现启动一个命名后台线程调用 `SpringApplication.exit(applicationContext, () -> 0)`，并配合 `server.shutdown=graceful`。
- 测试中替换或 mock 该接口，验证冻结流程会请求停机但不会终止测试 JVM。

## 事务边界

冻结消息和 `MsgAbstract` 保存应在一个事务内完成。`CentralPubkeyLockedMsgService` 使用 `TransactionTemplate` 包裹这两个写操作，避免 public 保存方法整体开启事务后让区块排空加入同一事务。`CentralPubkeyLockedMsgPersistenceService` 不再作为单独提交 shim 使用；如果没有其他调用方，应删除该类并更新结构式测试。

区块排空保持在 `BlockChainService.generateBlockUntilNoNotInBlockMsgs()` 的现有事务边界内。这样避免将可能较长的区块生成循环合并进冻结消息保存事务，同时保留区块生成 advisory lock 的串行化保护。

## 创世块和调度错误处理

`BlockDifficultyService.currentTransactionDifficultyTarget()` 不能直接解引用 `findTopByOrderByHeightDesc()`。当最新区块不存在时，应抛出 `ConflictException`，提示区块链尚未初始化。

`FlowNodeRegisterMsgService.saveFlowNodeRegisterMsg()` 读取注册难度时也应处理最新区块为空的情况，使用同样的 `ConflictException`，避免启动窗口内写请求产生 NPE。

`GenerateBlockTask.execute()` 增加顶层 try/catch 日志。失败时记录异常和耗时，不吞掉上下文信息；保持后续 fixed-delay tick 仍可重试。本轮不新增 Micrometer 指标。

## 配置和依赖

`src/main/resources/application.properties` 调整：

- `spring.flyway.baseline-version=0`
- `spring.jpa.hibernate.ddl-auto=validate`
- `server.shutdown=graceful`

`pom.xml` 调整：

- 将 BouncyCastle 从 `1.78` 升级到当前可用的兼容补丁版本。
- 不改变 Spring Boot、bitcoinj、Gatling 等非本轮目标依赖。

## 原语防御

`ByteArrayUtil.bytesToHex(byte[])`：

- 对 `null` 输入抛出 `IllegalArgumentException`。
- 使用 Java 21 `HexFormat.of().formatHex(bytes)` 替代循环内 `String.format`。

`PoWUtil.calculateTargetFromNBits(byte[])`：

- 保持 4 字节长度校验。
- 增加指数边界校验，拒绝会导致负指数或超出 256-bit 目标范围的 nBits。
- 对 mantissa 非正等无效值返回明确 `IllegalArgumentException`。

`SignatureValidator.validateLowS(byte[], String)`：

- 先校验签名非空且长度为 64 字节。
- 入参错误保持为 `IllegalArgumentException`，由现有全局异常处理映射为 400。

## 中心密钥对校验

`NmsciProperties.CentralKeyPair` 增加 `@AssertTrue`：

- 仅在 pubkey/prikey 基本 Base64 和长度校验通过后执行匹配检查。
- 使用现有 `Secp256k1EncryptUtil.rawToECKey(...)` 由私钥推导压缩公钥。
- 与配置的压缩公钥逐字节比较。
- 不匹配时启动期配置校验失败。

## API 文档

由于本轮明确不隐藏普通消息 `rawBytes`，需要同步文档，避免继续声明“所有消息实体 rawBytes 不输出”。`BlockInfo.rawBytes` 仍保持不输出；普通消息实体的 `rawBytes` 保持输出。

## 测试设计

新增或更新以下测试：

- 冻结流程保存成功后不调用 `System.exit(0)`，并能触发 `CentralPubkeyLockShutdownService.requestShutdown()`。
- `CentralPubkeyLockedMsg` 复用中心签名通路后，`centralSignature`、`rawBytes`、`txid` 的布局与协议预期一致。
- `CentralPubkeyLockedMsg` 实现 `CentrallySignedMessage`。
- `CentralPubkeyLockedMsgPersistenceService` 删除或不再被引用后，结构式测试同步更新。
- 中心密钥对不匹配时配置校验失败，匹配时通过。
- 创世块不存在时，难度读取和流转节点注册保存抛出明确业务异常。
- `GenerateBlockTask` 在区块生成异常时记录错误且不抛出到调度外层。
- `ByteArrayUtil.bytesToHex` 覆盖正常输入、空数组和 null 输入。
- `PoWUtil.calculateTargetFromNBits` 覆盖有效 nBits、负指数风险和超界指数。
- `SignatureValidator.validateLowS` 覆盖非 64 字节签名输入。
- 配置契约测试断言 Flyway baseline、DDL validate、graceful shutdown。
- API 文档或相关测试更新为普通消息 `rawBytes` 保持输出。

## 验证

实施完成后至少运行：

- `.\mvnw.cmd test`

如改动触及集成测试或 Testcontainers 场景可用，再运行：

- `.\mvnw.cmd verify`

若本机 Docker 不可用，应明确记录未运行 `verify` 的原因。

## 风险和回滚

主要行为变化是中心公钥冻结后不再由业务 Service 直接强杀 JVM，而是请求优雅停机。该变化降低数据撕裂风险，但需要测试确认冻结请求能正常返回且应用会按预期停止。

BouncyCastle 升级可能影响签名解析细节，必须依赖现有 crypto 单测和协议集成测试验证。

Flyway baseline 改为 0 会修复非空无历史表数据库跳过 V1 的问题；对已有 Flyway history 的环境不应重新执行 V1。

普通消息 `rawBytes` 保持输出，不产生客户端兼容性破坏。
