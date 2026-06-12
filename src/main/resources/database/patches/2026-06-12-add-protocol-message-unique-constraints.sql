-- Fix: protect protocol message uniqueness at the database layer.
-- Database: PostgreSQL
--
-- Run the duplicate checks first. If any query returns rows, resolve the
-- duplicate protocol messages manually before adding the unique constraints.

select flow_node_pubkey, count(*) as cnt
from flow_node_register_msgs
group by flow_node_pubkey
having count(*) > 1;

select txid, count(*) as cnt
from flow_node_register_msgs
group by txid
having count(*) > 1;

select flow_node_pubkey, count(*) as cnt
from central_pubkey_empower_msgs
group by flow_node_pubkey
having count(*) > 1;

select txid, count(*) as cnt
from central_pubkey_empower_msgs
group by txid
having count(*) > 1;

select central_pubkey, count(*) as cnt
from central_pubkey_locked_msgs
group by central_pubkey
having count(*) > 1;

select txid, count(*) as cnt
from central_pubkey_locked_msgs
group by txid
having count(*) > 1;

select flow_node_pubkey, count(*) as cnt
from flow_node_locked_msgs
group by flow_node_pubkey
having count(*) > 1;

select txid, count(*) as cnt
from flow_node_locked_msgs
group by txid
having count(*) > 1;

select txid, count(*) as cnt
from transaction_record_msgs
group by txid
having count(*) > 1;

select txid, count(*) as cnt
from transaction_mount_msgs
group by txid
having count(*) > 1;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_flow_node_register_msgs_flow_node_pubkey'
    ) then
        alter table flow_node_register_msgs
            add constraint uk_flow_node_register_msgs_flow_node_pubkey
            unique (flow_node_pubkey);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_flow_node_register_msgs_txid'
    ) then
        alter table flow_node_register_msgs
            add constraint uk_flow_node_register_msgs_txid
            unique (txid);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_central_pubkey_empower_msgs_flow_node_pubkey'
    ) then
        alter table central_pubkey_empower_msgs
            add constraint uk_central_pubkey_empower_msgs_flow_node_pubkey
            unique (flow_node_pubkey);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_central_pubkey_empower_msgs_txid'
    ) then
        alter table central_pubkey_empower_msgs
            add constraint uk_central_pubkey_empower_msgs_txid
            unique (txid);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_central_pubkey_locked_msgs_central_pubkey'
    ) then
        alter table central_pubkey_locked_msgs
            add constraint uk_central_pubkey_locked_msgs_central_pubkey
            unique (central_pubkey);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_central_pubkey_locked_msgs_txid'
    ) then
        alter table central_pubkey_locked_msgs
            add constraint uk_central_pubkey_locked_msgs_txid
            unique (txid);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_flow_node_locked_msgs_flow_node_pubkey'
    ) then
        alter table flow_node_locked_msgs
            add constraint uk_flow_node_locked_msgs_flow_node_pubkey
            unique (flow_node_pubkey);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_flow_node_locked_msgs_txid'
    ) then
        alter table flow_node_locked_msgs
            add constraint uk_flow_node_locked_msgs_txid
            unique (txid);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_transaction_record_msgs_txid'
    ) then
        alter table transaction_record_msgs
            add constraint uk_transaction_record_msgs_txid
            unique (txid);
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'uk_transaction_mount_msgs_txid'
    ) then
        alter table transaction_mount_msgs
            add constraint uk_transaction_mount_msgs_txid
            unique (txid);
    end if;
end $$;

select conname
from pg_constraint
where conname in (
    'uk_flow_node_register_msgs_flow_node_pubkey',
    'uk_flow_node_register_msgs_txid',
    'uk_central_pubkey_empower_msgs_flow_node_pubkey',
    'uk_central_pubkey_empower_msgs_txid',
    'uk_central_pubkey_locked_msgs_central_pubkey',
    'uk_central_pubkey_locked_msgs_txid',
    'uk_flow_node_locked_msgs_flow_node_pubkey',
    'uk_flow_node_locked_msgs_txid',
    'uk_transaction_record_msgs_txid',
    'uk_transaction_mount_msgs_txid'
);
