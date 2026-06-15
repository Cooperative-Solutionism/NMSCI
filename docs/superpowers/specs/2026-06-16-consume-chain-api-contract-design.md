# 消费链接口分页与 404 语义修复设计

## 背景

`docs/code-quality-audit-status.md` 将下一轮较高价值修复聚焦在两个 Medium 项：

1. `/consume-chains/edges` 返回裸 `List`，无分页或硬上限，`endTime` 默认 `Long.MAX_VALUE`，在大数据量下会放大 DoS 面。
2. not-found 语义不一致：部分 `getById` 路径已经通过 `EntityLookup` 返回 404，但部分按 pubkey 或状态查询仍用 `IllegalArgumentException` 返回 400。

本设计选择“严格 REST 契约型修复”：收敛分页响应契约，并把“输入错误”和“目标不存在”明确区分。

## 目标

- `/consume-chains/edges` 改为分页响应，避免无界结果集。
- 保留现有查询语义：`target` 必填，`source` 可选，按 `chain` 去重，支持 id 查询或 pubkey 查询且二者不可混用。
- 将明确的资源不存在、未冻结、未授权、未注册场景改为 `NotFoundException`，由全局异常处理映射为 404。
- 保持格式错误、缺参、非法枚举、非法分页参数返回 400。
- 同步 API 文档、审计状态文档和测试。

## 非目标

- 不修复源码哈希排除集与失败兜底。
- 不重塑全局 `IllegalArgumentException` 映射策略。
- 不调整 `ResponseResult.failure` 响应结构。
- 不统一 POST 201、`consumes`、幂等语义等低优先级 API 打磨项。
- 不做写 Service 模板化、协议常量化、控制器工具抽取等结构性重构。

## API 契约

### `/consume-chains/edges`

现有参数保留：

- `sourceId`
- `targetId`
- `sourcePubkey`
- `targetPubkey`
- `currencyType`，默认 `1`
- `startTime`，默认 `0`
- `endTime`，默认 `9223372036854775807`

新增分页参数：

- `page`，默认 `0`
- `size`，默认 `50`

分页校验复用 `PageRequestUtil`：

- `page < 0` 返回 400。
- `size <= 0` 返回 400。
- `size > 200` 返回 400。

响应从裸数组改为 `SliceResponseDTO<ConsumeChainEdge>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "content": [],
    "page": 0,
    "size": 50,
    "numberOfElements": 0,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

这是一次有意的破坏性 API 修复。裸数组无法表达分页状态，也无法可靠限制调用方的翻页行为。

## 实现设计

### Controller

`ConsumeChainController.getConsumeChainEdges` 新增 `page` 和 `size` 参数，返回类型改为：

```java
ResponseResult<SliceResponseDTO<ConsumeChainEdge>>
```

它继续负责：

- 检查 id 与 pubkey 参数不能混用。
- 检查目标参数必须存在。
- 将字符串 UUID 和 hex pubkey 转为服务层入参。
- 使用 `PageRequestUtil.of(page, size, EDGE_QUERY_SORT)` 构建 `Pageable`。

边查询必须稳定排序，`EDGE_QUERY_SORT` 固定为 `relatedTransactionMountTimestamp DESC, id DESC`，避免跨页抖动。Repository 内部仍用 `DISTINCT ON (c.chain)` 选择每条 chain 的代表边：子查询维持“每 chain 取最早相关挂载时间”的既有语义，外层再按响应排序输出。

### Service

`ConsumeChainQueryService` 的边查询方法改为返回 `Slice<ConsumeChainEdge>`：

- `getConsumeChainEdgesById(..., Pageable pageable)`
- `getConsumeChainEdgesByPubkey(..., Pageable pageable)`

服务层继续保留：

- `currencyType` 校验。
- `targetId` 非空校验。
- pubkey 查询时先解析 source/target 流转节点。

`sourceId == null` 时仍查询流入 target 的全部边；`sourceId != null` 时查询 source 到 target 的边。

### Repository

原生 SQL 改为分页查询。为避免昂贵 `COUNT(*)`，使用 `Slice` 风格：

- repository 查询 `pageSize + 1` 条。
- service 截断到 `pageSize`。
- 若多出第 `pageSize + 1` 条，则 `hasNext=true`。

Service 从 `Pageable` 派生 `limit = pageSize + 1` 与 `offset = pageable.getOffset()`，显式传给 native query，保持 SQL 简单可控。

## 错误契约

继续返回 400：

- `targetId` / `targetPubkey` 未提供。
- id 与 pubkey 参数混用。
- UUID 格式错误。
- pubkey 为空、非 hex、长度不是 33 字节。
- `currencyType` 非法。
- 分页参数非法。

改为返回 404：

- `CentralPubkeyLockedMsgService.getCentralPubkeyLockedMsgByCentralPubkey` 查询的中心公钥未冻结。
- `FlowNodeLockedMsgService.getFlowNodeLockedMsgByFlowNodePubkey` 查询的流转节点公钥未冻结。
- `CentralPubkeyEmpowerMsgService.getCentralPubkeyEmpowerMsgByFlowNodePubkey` 查询的流转节点公钥未授权。
- `ConsumeChainQueryService` 通过 pubkey 查 source、target、node 时目标流转节点不存在。当前 `ConsumeChainSupport.getFlowNodeRegisterMsgByPubkey` 已抛 `NotFoundException`，保持并补充覆盖。

不把所有 `IllegalArgumentException` 全局改为 404。它仍表示调用方输入格式或请求结构错误。

## 文档更新

更新 `docs/API.md`：

- `/consume-chains/edges` 响应从列表改为分页对象。
- 明确 `page` / `size` 参数和 `size <= 200` 上限。
- 更新 400 与 404 示例或描述。

更新 `docs/code-quality-audit-status.md`：

- 将 `/consume-chains/edges` 未分页标记为已修复。
- 将 not-found 语义不一致标记为已修复或部分收口，并说明格式错误仍为 400。
- 将 `mvnw verify` 的状态按实际运行结果更新。

## 测试策略

### Controller/API 契约

- `/consume-chains/edges` 返回 `SliceResponseDTO` 结构，而不是裸数组。
- `page` / `size` 参与响应元数据。
- `size > 200` 返回 400。
- id/pubkey 混用返回 400。
- 缺少 target 返回 400。

### Service/Repository 行为

- 边查询只返回当前页内容。
- 当查询到 `size + 1` 条时，响应 `hasNext=true` 且 content 截断为 `size`。
- `source` 为空时仍表示“流入 target 的全部边，按 chain 去重”。
- `source` 存在时仍表示 “source -> target 的边，按 chain 去重”。

### 404 语义

- 未冻结中心公钥返回 404。
- 未冻结流转节点公钥返回 404。
- 未授权流转节点公钥返回 404。
- consume-chain pubkey 查询中目标流转节点不存在返回 404。
- pubkey 长度错误仍返回 400。

### 验收命令

- 先跑目标测试，覆盖 controller/service/repository/not-found 改动。
- 再跑 `.\mvnw.cmd test`。
- Docker 可用时跑 `.\mvnw.cmd verify`，并将结果写回审计状态文档。

## 风险与兼容性

- `/consume-chains/edges` 响应从数组改为分页对象，是破坏性变更。该变更是有意的，因为原裸列表契约无法控制 DoS 面。
- Native SQL 分页要保持 `DISTINCT ON (c.chain)` 的既有代表边选择语义，避免分页修复改变业务含义。
- not-found 修复不能把格式错误误改成 404；测试必须覆盖 400/404 分界。
