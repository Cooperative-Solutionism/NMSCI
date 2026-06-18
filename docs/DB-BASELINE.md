# 数据库基线与升级（Flyway）运维手册

本手册说明：从「无 Flyway 的线上 v1.0.0 库」升级到「Flyway 托管的新版本」时如何安全基线，以及全新部署的行为。

## 背景

线上 v1.0.0 的数据库模式是由 Hibernate 自动建表生成的，**只有表 + 主键 + 外键**，且**没有 `flyway_schema_history` 表**。
而本项目的迁移脚本设计了更完整的对象（唯一约束、检查约束、查询索引）。两者存在差异：

| 对象类别 | 迁移脚本 | 线上 v1.0.0 实际 |
| --- | --- | --- |
| 表 / 列 / 默认值 / 注释 | 有 | 有（一致） |
| 主键（含 `block_info_pkey`、`all_type_msg_abstracts_pkey`） | 有 | 有（一致） |
| 外键（`fk_*`，consume_chains / consume_chain_edges 共 7 条） | 有 | 有（一致） |
| 唯一约束（13 条 `uk_*`） | 有 | **无** |
| 检查约束（`ck_transaction_record_msgs_amount_positive`） | 有 | **无** |
| 查询索引（约 20 条 `idx_*`） | 有 | **无** |

## 迁移布局

为让「线上既有库」与「全新空库」收敛到同一最终模式，迁移按如下分层：

- **V1 `baseline`**：忠实快照线上 v1.0.0 的真实模式（仅表 + 主键 + 外键 + 注释）。
- **V2 `add_designed_constraints_and_indexes`**：补齐设计期本应存在、但自动建表未生成的 `uk_*` / `ck_` / `idx_*`。
- **V3 `harden_db_constraints`**：评审 #3 硬化（`fk_transaction_mount_mounted_record` + 消费链/边 `amount > 0`）。
- **V4 `support_central_pubkey_rotation`**：公钥轮换（放宽公证唯一性为 `(流转节点公钥, 中心公钥)` 组合）。

配置：`spring.flyway.baseline-on-migrate=true`、`spring.flyway.baseline-version=1`。

两类库的行为：

- **线上既有库**（非空、无 `flyway_schema_history`）：Flyway 自动基线到 **V1**（**不重跑** V1 建表），随后执行 **V2 / V3 / V4**。
- **全新空库**：Flyway 忽略 baseline，从 **V1** 起执行 **全部** 迁移。

> 关键前提：本策略要求 Flyway 此前从未在任何持久库成功运行过（即除这台线上库外没有已写入 `flyway_schema_history` 的预发/测试库）。本项目 dev/test 用临时库，集成测试用 Testcontainers 一次性容器，均不违反该前提。

## 上线前步骤（线上既有库）

1. **备份**：完整备份数据库；同时按成对备份策略备份 `file/dat` 区块文件（见 README「运维与恢复」）。
2. **数据预检（必做）**：V2/V3 会向既有数据补加约束，若数据已违反则 `ALTER TABLE ... ADD CONSTRAINT` 失败、应用启动失败。执行：

   ```bash
   psql "$DB_URL" -f docs/prod_baseline_precheck.sql
   ```

   要求所有 `violations` 为 **0**。任意非 0 项必须先修数据再上线（重复 `txid`/公钥/高度、孤儿挂载引用、非正 `amount` 等）。
3. **确认前提**：确认线上库当前**没有** `flyway_schema_history` 表，且没有其它预发库已跑过 Flyway。

   ```sql
   select to_regclass('public.flyway_schema_history');  -- 期望 NULL
   ```

## 部署与验证

1. 用新版本启动应用。Flyway 将自动：建 `flyway_schema_history` → 写入 V1 基线行 → 执行 V2 / V3 / V4。
2. 验证迁移结果：

   ```sql
   select installed_rank, version, description, type, success
   from flyway_schema_history order by installed_rank;
   ```

   期望：`version=1` 为 `BASELINE`（success=t），`2/3/4` 为 `SQL` 且 `success=t`。
3. 抽查若干对象已就位：

   ```sql
   select conname from pg_constraint where conname = 'uk_block_infos_height';                 -- 应有 1 行
   select indexname from pg_indexes where indexname = 'idx_msg_abstracts_unblocked_timestamp'; -- 应有 1 行
   ```

## 失败与回滚

- 若 V2/V3 因数据违反约束失败，应用不会启动。**回滚**：从备份恢复（迁移在事务内，部分语句失败会回滚该脚本，但应以备份为准），修正数据后重试。
- 切勿手工往 `flyway_schema_history` 塞行来「绕过」失败，应定位数据问题（预检脚本即为定位工具）。

## 本地开发库注意

若本地 dev 库此前已用「旧布局」跑过 Flyway 并记录了 V1/V2/V3 的 checksum，本次重排（重写 V1、V2/V3 顺延为 V3/V4、新增 V2）会触发 checksum 不匹配或「已应用迁移缺失」。处理：对本地库执行 `flyway repair`，或直接 drop/recreate 本地 dev 库（本地数据可丢弃）。线上库不受影响（它从未跑过 Flyway）。
