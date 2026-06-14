# 并发可重入性审计（Phase 3 前置）

对装块与消费链分配两条写路径的并发模型审计，结论先行：**当前设计在并发下可重入、无双重分配，可安全进行 B 系列性能优化**。本文记录审计依据，作为 B2/B3 改造不破坏并发语义的基线。

## 1. 装块（block generation）

- `block/BlockGenerationLock.lock()` 以 `@Transactional(propagation = MANDATORY)` 执行 `select pg_advisory_xact_lock(<LOCK_KEY>)`。
  - `MANDATORY` 强制其必须在已存在的事务内调用；advisory **xact** 锁随该事务提交/回滚自动释放。
  - `BlockChainService.generateBlock()` / `generateBlockUntilNoNotInBlockMsgs()` 均为 `@Transactional` 且首行即 `lock()`，因此整个装块过程持有该 advisory 锁。
- 跨实例：同一 `LOCK_KEY` 的 advisory 锁保证任一时刻只有一个事务在装块。
- 单实例：`GenerateBlockTask.execute()` 为 `@Scheduled(initialDelay=0, fixedDelay=10min)`，单调度线程 + `fixedDelay`（上一次完成后再计时）天然不重叠。
- **结论**：装块串行化成立（advisory 锁负责跨实例，调度模型负责实例内）。

## 2. 消费链分配（allocation）

- 入口 `TransactionMountMsgService.saveTransactionMountMsg`（`@Transactional`）→ `ConsumeChainAllocationService.saveConsumeChain`（`@Transactional`，同一外层事务）。
- 候选链读取使用 `PESSIMISTIC_WRITE`（`SELECT ... FOR UPDATE`），过滤 `is_loop = false AND end = <源节点> AND currency_type = ?`，按 `tail_mount_timestamp ASC` 取。
- 竞争边界：分配只消费“终点为本次 mount 源节点”的开放链。
  - 不同源节点的并发 mount 触及不相交的候选行，互不阻塞。
  - 同一源节点的并发 mount 在候选行的 `FOR UPDATE` 上串行：后者阻塞至前者提交，再在 `READ COMMITTED` 下按 EvalPlanQual 重读最新行版本；已被前者成环（`is_loop = true`）的链被 `is_loop = false` 过滤排除，**不会被二次分配**。
- 分配器 `consume/ConsumeChainAllocator.allocate` 对**任意候选集合**都安全：按序消费（整额 extend / 不足额 split），最终若仍有余额则 `createRestChain` 新建链。因此候选集合即使因并发成环而变短，也只是更多金额落入新链，**不构成正确性缺陷**。
- 唯一性兜底：`transaction_mount_msgs.mounted_transaction_record_id` 有 DB 唯一约束 + `findByIdForUpdate` 悲观锁，保证同一交易记录只被挂载一次。

## 3. 对 B2 优化的约束（不可破坏的不变量）

将分页循环取数改为单条窗口累计和查询时，必须保持：

1. 选中的候选链以 `FOR UPDATE` 加悲观写锁；
2. `is_loop = false` 过滤在**加锁层**生效（避免锁到已成环链）；
3. 排序语义 `tail_mount_timestamp ASC`（决定优先消费更早的链）；
4. “按需取刚好够覆盖交易额的最小前缀”，不足部分由分配器新建链承接（与现状一致）。

满足以上四点即与现有分页循环并发等价，仅减少 round-trip。
