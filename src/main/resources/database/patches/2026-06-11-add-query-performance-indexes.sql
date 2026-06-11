-- Add indexes for hot validation, block generation, transaction query, and consume-chain paths.
-- Database: PostgreSQL
--
-- These are non-unique indexes only. Protocol-level uniqueness such as
-- flow_node_pubkey should be confirmed separately before adding unique indexes.

-- Message validation
create index if not exists idx_flow_node_register_pubkey
    on flow_node_register_msgs(flow_node_pubkey);

create index if not exists idx_flow_node_locked_pubkey
    on flow_node_locked_msgs(flow_node_pubkey);

create index if not exists idx_central_pubkey_locked_pubkey
    on central_pubkey_locked_msgs(central_pubkey);

create index if not exists idx_central_pubkey_empower_flow_central
    on central_pubkey_empower_msgs(flow_node_pubkey, central_pubkey);

-- Transaction query
create index if not exists idx_transaction_record_consume_pubkey
    on transaction_record_msgs(consume_node_pubkey);

create index if not exists idx_transaction_record_flow_pubkey
    on transaction_record_msgs(flow_node_pubkey);

create index if not exists idx_transaction_record_consume_flow_pubkey
    on transaction_record_msgs(consume_node_pubkey, flow_node_pubkey);

create index if not exists idx_transaction_mount_consume_pubkey
    on transaction_mount_msgs(consume_node_pubkey);

create index if not exists idx_transaction_mount_flow_pubkey
    on transaction_mount_msgs(flow_node_pubkey);

create index if not exists idx_transaction_mount_consume_flow_pubkey
    on transaction_mount_msgs(consume_node_pubkey, flow_node_pubkey);

-- Block generation
create index if not exists idx_msg_abstracts_unblocked_timestamp
    on msg_abstracts(is_in_block, confirm_timestamp);

create index if not exists idx_msg_abstracts_unblocked_timestamp_id
    on msg_abstracts(is_in_block, confirm_timestamp, id);

create index if not exists idx_block_infos_height_desc
    on block_infos(height desc);

-- Consume chain
create index if not exists idx_consume_chains_open_end_currency_tail
    on consume_chains("end", currency_type, tail_mount_timestamp)
    where is_loop = false;

create index if not exists idx_consume_chains_start
    on consume_chains(start);

create index if not exists idx_consume_chains_start_loop
    on consume_chains(start, is_loop);

create index if not exists idx_consume_chains_end
    on consume_chains("end");

create index if not exists idx_consume_chains_end_loop
    on consume_chains("end", is_loop);

create index if not exists idx_consume_chain_edges_chain_timestamp
    on consume_chain_edges(chain, related_transaction_mount_timestamp);

create index if not exists idx_consume_chain_edges_mount
    on consume_chain_edges(related_transaction_mount);

create index if not exists idx_consume_chain_edges_source_target_currency_time
    on consume_chain_edges(source, target, currency_type, related_transaction_mount_timestamp, chain);

create index if not exists idx_consume_chain_edges_target_currency_time
    on consume_chain_edges(target, currency_type, related_transaction_mount_timestamp, chain);
