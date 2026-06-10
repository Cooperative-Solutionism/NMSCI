-- Fix: prevent the same transaction_record_msg from being mounted more than once.
-- Database: PostgreSQL
--
-- Run the duplicate check first. If it returns rows, resolve the duplicate
-- transaction_mount_msgs records manually before adding the unique constraint.

select mounted_transaction_record_id, count(*) as cnt
from transaction_mount_msgs
group by mounted_transaction_record_id
having count(*) > 1;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'uk_transaction_mount_msgs_mounted_transaction_record_id'
    ) then
        alter table transaction_mount_msgs
            add constraint uk_transaction_mount_msgs_mounted_transaction_record_id
            unique (mounted_transaction_record_id);
    end if;
end $$;

select conname
from pg_constraint
where conname = 'uk_transaction_mount_msgs_mounted_transaction_record_id';
