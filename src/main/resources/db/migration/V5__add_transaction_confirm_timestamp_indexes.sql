-- V5 交易查询时间戳索引（性能审计 QW1/finding #2）：交易记录/挂载的 search() 支持按 confirm_timestamp
-- 范围过滤，且所有消息分页统一按 (confirm_timestamp desc, id desc) 排序（见 PageRequestUtil.MESSAGE_QUERY_SORT）。
-- V2 仅建了 pubkey 等值索引，未覆盖时间戳范围与排序：纯时间窗查询会退化为全表顺序扫描，pubkey+时间窗查询
-- 也无法用索引裁剪范围，且分页排序需额外 Sort。本迁移补齐支撑索引，纯增量、无删除。
-- 与冻结的字节协议/区块格式/版本号无关；仅新增索引，不改数据，无需数据预检。
--
-- 索引列序与排序方向对齐查询：pubkey 等值列在前，随后 (confirm_timestamp desc, id desc) 直接满足过滤+排序，
-- 使 pubkey+时间窗、单 pubkey 分页均可索引扫描而免于额外 Sort；末两条 (confirm_timestamp desc, id desc)
-- 覆盖仅按时间窗/无过滤的全表分页，消除顺序扫描。

-- 交易记录：消费/流转节点等值 + 时间窗 + 排序
create index idx_transaction_record_consume_confirm_ts
    on transaction_record_msgs(consume_node_pubkey, confirm_timestamp desc, id desc);

create index idx_transaction_record_flow_confirm_ts
    on transaction_record_msgs(flow_node_pubkey, confirm_timestamp desc, id desc);

create index idx_transaction_record_confirm_ts
    on transaction_record_msgs(confirm_timestamp desc, id desc);

-- 交易挂载：消费/流转节点等值 + 时间窗 + 排序
create index idx_transaction_mount_consume_confirm_ts
    on transaction_mount_msgs(consume_node_pubkey, confirm_timestamp desc, id desc);

create index idx_transaction_mount_flow_confirm_ts
    on transaction_mount_msgs(flow_node_pubkey, confirm_timestamp desc, id desc);

create index idx_transaction_mount_confirm_ts
    on transaction_mount_msgs(confirm_timestamp desc, id desc);
