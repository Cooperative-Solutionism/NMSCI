-- 删除所有表中的数据
TRUNCATE TABLE central_pubkey_empower_msgs CASCADE;
TRUNCATE TABLE central_pubkey_locked_msgs CASCADE;
TRUNCATE TABLE flow_node_locked_msgs CASCADE;
TRUNCATE TABLE flow_node_register_msgs CASCADE;
TRUNCATE TABLE transaction_mount_msgs CASCADE;
TRUNCATE TABLE transaction_record_msgs CASCADE;
TRUNCATE TABLE block_infos CASCADE;
TRUNCATE TABLE msg_abstracts CASCADE;
TRUNCATE TABLE consume_chains CASCADE;
TRUNCATE TABLE consume_chain_edges CASCADE;
