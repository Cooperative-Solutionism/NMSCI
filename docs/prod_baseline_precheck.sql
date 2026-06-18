-- NMSCI 线上 v1.0.0 库 Flyway 基线上线前「数据预检」。
-- 目的：V2 迁移会向既有数据补加唯一约束/检查约束、V3 会补加外键与金额检查；
--       若现有数据已违反这些约束，ADD CONSTRAINT 会失败导致应用启动失败。
-- 用法：以只读身份连上线上库执行本脚本（psql -f docs/prod_baseline_precheck.sql）。
-- 判读：所有 violations 必须为 0 方可部署；任何非 0 项须先修数据再上线。详见 docs/DB-BASELINE.md。

\echo '== NMSCI Flyway 基线上线前数据预检 (所有 violations 必须为 0) =='

select check_name, violations
from (
    -- V2 唯一约束：重复值会使 ADD CONSTRAINT ... UNIQUE 失败
    select 'V2 uk flow_node_register_msgs.flow_node_pubkey' as check_name,
           count(*) as violations
    from (select flow_node_pubkey from flow_node_register_msgs group by flow_node_pubkey having count(*) > 1) d
    union all
    select 'V2 uk flow_node_register_msgs.txid',
           count(*)
    from (select txid from flow_node_register_msgs group by txid having count(*) > 1) d
    union all
    select 'V2 uk central_pubkey_empower_msgs.flow_node_pubkey',
           count(*)
    from (select flow_node_pubkey from central_pubkey_empower_msgs group by flow_node_pubkey having count(*) > 1) d
    union all
    select 'V2 uk central_pubkey_empower_msgs.txid',
           count(*)
    from (select txid from central_pubkey_empower_msgs group by txid having count(*) > 1) d
    union all
    select 'V2 uk central_pubkey_locked_msgs.central_pubkey',
           count(*)
    from (select central_pubkey from central_pubkey_locked_msgs group by central_pubkey having count(*) > 1) d
    union all
    select 'V2 uk central_pubkey_locked_msgs.txid',
           count(*)
    from (select txid from central_pubkey_locked_msgs group by txid having count(*) > 1) d
    union all
    select 'V2 uk flow_node_locked_msgs.flow_node_pubkey',
           count(*)
    from (select flow_node_pubkey from flow_node_locked_msgs group by flow_node_pubkey having count(*) > 1) d
    union all
    select 'V2 uk flow_node_locked_msgs.txid',
           count(*)
    from (select txid from flow_node_locked_msgs group by txid having count(*) > 1) d
    union all
    select 'V2 uk transaction_record_msgs.txid',
           count(*)
    from (select txid from transaction_record_msgs group by txid having count(*) > 1) d
    union all
    select 'V2 uk transaction_mount_msgs.mounted_transaction_record_id',
           count(*)
    from (select mounted_transaction_record_id from transaction_mount_msgs group by mounted_transaction_record_id having count(*) > 1) d
    union all
    select 'V2 uk transaction_mount_msgs.txid',
           count(*)
    from (select txid from transaction_mount_msgs group by txid having count(*) > 1) d
    union all
    select 'V2 uk block_infos.height',
           count(*)
    from (select height from block_infos group by height having count(*) > 1) d
    union all
    select 'V2 uk block_infos.previous_block_hash',
           count(*)
    from (select previous_block_hash from block_infos group by previous_block_hash having count(*) > 1) d
    union all
    -- V2 检查约束：amount > 0
    select 'V2 ck transaction_record_msgs.amount > 0',
           count(*)
    from transaction_record_msgs where amount <= 0
    union all
    -- V3 外键：挂载信息引用的交易记录必须存在（孤儿引用会使 ADD FOREIGN KEY 失败）
    select 'V3 fk transaction_mount_msgs.mounted_transaction_record_id -> transaction_record_msgs.id',
           count(*)
    from transaction_mount_msgs m
        left join transaction_record_msgs r on m.mounted_transaction_record_id = r.id
    where r.id is null
    union all
    -- V3 检查约束：消费链/边金额 > 0
    select 'V3 ck consume_chains.amount > 0',
           count(*)
    from consume_chains where amount <= 0
    union all
    select 'V3 ck consume_chain_edges.amount > 0',
           count(*)
    from consume_chain_edges where amount <= 0
) checks
order by violations desc, check_name;

\echo '== 预检结束：若上表任意 violations > 0，请先修数据，切勿部署 =='
