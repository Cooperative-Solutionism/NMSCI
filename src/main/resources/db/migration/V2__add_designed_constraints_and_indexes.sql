-- V2 设计期完整性与查询索引：补齐 V1 真实快照（线上由 Hibernate 自动建表，仅表+主键+外键）所缺的
-- 唯一约束、检查约束与查询性能索引。这些是 v1.0.0 设计意图中本应存在、但自动建表未生成的对象。
-- 在「线上既有库」（baseline 跳过 V1 后执行本脚本）与「全新空库」（V1 建表后执行本脚本）上结果一致，
-- 使两类库收敛到同一模式。与冻结的字节协议/区块格式/版本号无关。
--
-- 上线前提（线上既有库）：现有数据须已满足下列唯一/检查约束，否则 ADD CONSTRAINT 会失败。
-- 部署前必须确认存量数据无违例（重复 txid/pubkey/height、非正 amount 等），否则 ADD CONSTRAINT 会在启动时失败。

-- 协议消息唯一性：txid 全局唯一，关键公钥/高度/父哈希按协议语义唯一。
alter table flow_node_register_msgs
    add constraint uk_flow_node_register_msgs_flow_node_pubkey unique (flow_node_pubkey);
alter table flow_node_register_msgs
    add constraint uk_flow_node_register_msgs_txid unique (txid);

alter table central_pubkey_empower_msgs
    add constraint uk_central_pubkey_empower_msgs_flow_node_pubkey unique (flow_node_pubkey);
alter table central_pubkey_empower_msgs
    add constraint uk_central_pubkey_empower_msgs_txid unique (txid);

alter table central_pubkey_locked_msgs
    add constraint uk_central_pubkey_locked_msgs_central_pubkey unique (central_pubkey);
alter table central_pubkey_locked_msgs
    add constraint uk_central_pubkey_locked_msgs_txid unique (txid);

alter table flow_node_locked_msgs
    add constraint uk_flow_node_locked_msgs_flow_node_pubkey unique (flow_node_pubkey);
alter table flow_node_locked_msgs
    add constraint uk_flow_node_locked_msgs_txid unique (txid);

alter table transaction_record_msgs
    add constraint uk_transaction_record_msgs_txid unique (txid);

alter table transaction_mount_msgs
    add constraint uk_transaction_mount_msgs_mounted_transaction_record_id unique (mounted_transaction_record_id);
alter table transaction_mount_msgs
    add constraint uk_transaction_mount_msgs_txid unique (txid);

alter table block_infos
    add constraint uk_block_infos_height unique (height);
alter table block_infos
    add constraint uk_block_infos_previous_block_hash unique (previous_block_hash);

-- 金额正值检查：交易记录金额须为正。
alter table transaction_record_msgs
    add constraint ck_transaction_record_msgs_amount_positive check (amount > 0);

-- Message validation indexes
create index idx_flow_node_register_pubkey
    on flow_node_register_msgs(flow_node_pubkey);

create index idx_flow_node_locked_pubkey
    on flow_node_locked_msgs(flow_node_pubkey);

create index idx_central_pubkey_locked_pubkey
    on central_pubkey_locked_msgs(central_pubkey);

create index idx_central_pubkey_empower_flow_central
    on central_pubkey_empower_msgs(flow_node_pubkey, central_pubkey);

-- Transaction query indexes
create index idx_transaction_record_consume_pubkey
    on transaction_record_msgs(consume_node_pubkey);

create index idx_transaction_record_flow_pubkey
    on transaction_record_msgs(flow_node_pubkey);

create index idx_transaction_record_consume_flow_pubkey
    on transaction_record_msgs(consume_node_pubkey, flow_node_pubkey);

create index idx_transaction_mount_consume_pubkey
    on transaction_mount_msgs(consume_node_pubkey);

create index idx_transaction_mount_flow_pubkey
    on transaction_mount_msgs(flow_node_pubkey);

create index idx_transaction_mount_consume_flow_pubkey
    on transaction_mount_msgs(consume_node_pubkey, flow_node_pubkey);

-- Block generation indexes
create index idx_msg_abstracts_unblocked_timestamp
    on msg_abstracts(is_in_block, confirm_timestamp);

create index idx_msg_abstracts_unblocked_timestamp_id
    on msg_abstracts(is_in_block, confirm_timestamp, id);

create index idx_block_infos_height_desc
    on block_infos(height desc);

-- Consume chain indexes
create index idx_consume_chains_open_end_currency_tail
    on consume_chains("end", currency_type, tail_mount_timestamp)
    where is_loop = false;

create index idx_consume_chains_start
    on consume_chains(start);

create index idx_consume_chains_start_loop
    on consume_chains(start, is_loop);

create index idx_consume_chains_end
    on consume_chains("end");

create index idx_consume_chains_end_loop
    on consume_chains("end", is_loop);

create index idx_consume_chain_edges_chain_timestamp
    on consume_chain_edges(chain, related_transaction_mount_timestamp);

create index idx_consume_chain_edges_mount
    on consume_chain_edges(related_transaction_mount);

create index idx_consume_chain_edges_source_target_currency_time
    on consume_chain_edges(source, target, currency_type, related_transaction_mount_timestamp, chain);

create index idx_consume_chain_edges_target_currency_time
    on consume_chain_edges(target, currency_type, related_transaction_mount_timestamp, chain);
