# Secp256k1 原语负路径测试与边界守卫设计

## 背景

`docs/code-quality-audit-status.md` 将「Secp256k1 原语负路径测试缺失」列为下一轮较高价值修复项。当前 `Secp256k1EncryptUtilTest` 主要覆盖正常签名、验签、DER/RS 往返，缺少 malformed DER、错误密钥、篡改数据、非法公私钥、high-S、错误长度等负路径。

同时，`Secp256k1EncryptUtil` 的少数 primitive 方法仍把边界校验交给下游实现或数组切片行为：

- `derToRs` 会解析 DER 后直接取 `r/s` 的 `toByteArray()`，超过 32 字节时按尾部截断，缺少 ECDSA 标量范围校验。
- `isNotLowS` 直接切片 `rsSignature[32..64)`，对 `null` 或非 64 字节输入没有稳定的 primitive 层错误契约。
- `rsToDer`、`compressedToPublicKey`、`rawToPrivateKey` 已有部分长度/范围校验，但测试覆盖不足，且 `rawToPrivateKey(null)` 目前依赖空指针行为。

本轮选择「测试 + 小范围生产守卫」方案：先用测试固定期望，再补充低层工具类的明确拒绝行为。

## 目标

- 扩展 `Secp256k1EncryptUtilTest`，覆盖真实 primitive 负路径，而不是只依赖 `SignatureValidator` 服务层测试。
- 错误密钥、篡改数据应稳定返回验签失败，而不是抛出不相关异常。
- malformed DER、非 2 元素 DER、非法 `r/s` 标量应在 `derToRs` 入口被拒绝。
- high-S RS 签名应能被 `isNotLowS` 明确识别。
- `isNotLowS`、`rsToDer`、`rawToPrivateKey`、`rawToECKey`、`compressedToPublicKey` 对 `null`、错误长度、非法范围输入给出稳定异常。
- 保持现有正常签名、验签、DER/RS 往返行为不变。

## 非目标

- 不引入新加密库或替换 bitcoinj/BouncyCastle。
- 不重写签名、验签、hash 或密钥派生算法。
- 不改变签名数据的双 SHA-256 语义。
- 不调整 `SignatureValidator` 的错误映射策略，除非 primitive 方法签名变化导致必须同步测试。
- 不处理并发测试、API 错误契约、结构性重构或 rawBytes 输出策略。

## 设计方案

### 1. 测试优先扩展

在 `src/test/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtilTest.java` 增加 focused 单元测试：

- `verifySignatureRejectsWrongPublicKey`：用 A 私钥签名，用 B 公钥验签，应返回 `false`。
- `verifySignatureRejectsTamperedData`：签名后修改原始数据，应返回 `false`。
- `rsToDerRejectsNonRsLength`：长度不是 64 字节时抛 `IllegalArgumentException`。
- `isNotLowSDetectsHighS`：构造 `s = n/2 + 1` 的 RS，确认返回 `true`。
- `isNotLowSRejectsInvalidLength`：`null`、短数组、长数组均稳定抛 `IllegalArgumentException`。
- `derToRsRejectsMalformedDer`：`null`、随机字节、截断 DER、非 sequence 顶层、空序列、三元素序列、非 integer 元素均被稳定拒绝。
- `derToRsRejectsInvalidScalars`：`r/s <= 0` 或 `r/s >= n` 被拒绝，避免非法签名标量进入 RS。
- `compressedToPublicKeyRejectsInvalidInput`：`null`、非 33 字节、错误前缀或无效点被拒绝。
- `rawPrivateKeyConversionRejectsInvalidInput`：`null`、非 32 字节、全 0、等于曲线阶、超过曲线阶均被拒绝。

测试构造曲线阶时使用 BouncyCastle `SECNamedCurves.getByName("secp256k1").getN()`，不反射生产类私有常量。

### 2. primitive 层小范围守卫

在 `src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java` 做集中、低风险校验：

- 为 RS 输入增加共享校验：非 `null` 且长度必须为 64。
- `isNotLowS` 复用 RS 长度校验，避免数组切片制造不稳定异常。
- `rsToDer` 增加 `null` 防御，保持错误长度仍为 `IllegalArgumentException`。
- `rawToPrivateKey` 增加 `null` 防御，与 `rawToECKey` 的错误契约对齐。
- `compressedToPublicKey` 增加 `null` 防御；无效点仍可由 BouncyCastle 解码抛出。
- `derToRs` 解析后校验：
  - DER 顶层必须是 ASN.1 sequence。
  - sequence 必须正好包含两个 ASN.1 integer。
  - `r`、`s` 必须满足 `1 <= value < CURVE_ORDER`。
  - 输出仍是固定 64 字节 RS；合法 DER 的前导 0 仅作为正数编码存在，不视为错误。

`derToRs` 对 ASN.1 结构错误继续使用 `IOException` 表达，因为当前方法签名已经声明该受检异常；其他工具方法的空值、错误长度或明确非法参数继续使用 `IllegalArgumentException`。

### 3. 保持分层边界

`SignatureValidator` 仍是协议层入口，继续负责业务错误码和消息封装。本轮不把所有 primitive 异常改写成业务异常，只确保 primitive 层自身的拒绝行为可预测、可测试。

## 测试策略

按 TDD 执行：

1. 先新增 `Secp256k1EncryptUtilTest` 负路径用例，运行 focused test，确认新增用例失败。
2. 实现最小 primitive 守卫。
3. 重新运行 focused test，确认通过。
4. 运行全量单元测试和 verify。

验收命令：

```powershell
.\mvnw.cmd -Dtest=Secp256k1EncryptUtilTest test
.\mvnw.cmd test
.\mvnw.cmd verify
```

`verify` 仍依赖 Docker/Testcontainers 可用；若外部镜像或网络失败，应记录精确失败原因。

## 风险与权衡

- `derToRs` 拒绝非法标量会比当前实现更严格。该变化符合 ECDSA 签名格式约束，但如果外部调用者曾依赖“截断后继续处理”的行为，可能暴露兼容性问题；本项目内部不应依赖该行为。
- `isNotLowS` 从数组切片异常变成明确 `IllegalArgumentException`，属于错误契约收紧，不影响合法输入。
- 不统一所有异常类型，是为了避免在本轮把 crypto primitive 与 API 错误映射强耦合；服务层错误契约可作为后续单独任务处理。
