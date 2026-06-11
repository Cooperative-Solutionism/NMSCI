-- Fix: reject zero and negative transaction record amounts at the database boundary.
-- Run the invalid amount check first. If it returns rows, resolve those
-- transaction_record_msgs manually before adding the check constraint.

select id, amount
from transaction_record_msgs
where amount <= 0;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'ck_transaction_record_msgs_amount_positive'
    ) then
        alter table transaction_record_msgs
            add constraint ck_transaction_record_msgs_amount_positive
            check (amount > 0);
    end if;
end $$;

select conname
from pg_constraint
where conname = 'ck_transaction_record_msgs_amount_positive';
