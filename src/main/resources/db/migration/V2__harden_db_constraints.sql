-- 数据库完整性硬化（评审建议 #3）：补齐既有模式中缺失的引用完整性与金额正值约束。
-- 纯加性迁移，不改 V1__baseline.sql；与冻结的字节协议/区块格式/版本号无关。

-- 挂载信息引用的交易记录补外键。审计链中被引用的交易记录不可删除，故使用 ON DELETE RESTRICT；
-- 引用列已有 uk_transaction_mount_msgs_mounted_transaction_record_id 唯一约束提供支撑索引。
alter table transaction_mount_msgs
    add constraint fk_transaction_mount_mounted_record
        foreign key (mounted_transaction_record_id)
        references transaction_record_msgs (id)
        on delete restrict;

-- 消费链与边的金额由 amount>0 的交易记录派生，与 transaction_record_msgs.amount 的约束保持一致，须为正。
alter table consume_chains
    add constraint ck_consume_chains_amount_positive
        check (amount > 0);

alter table consume_chain_edges
    add constraint ck_consume_chain_edges_amount_positive
        check (amount > 0);
