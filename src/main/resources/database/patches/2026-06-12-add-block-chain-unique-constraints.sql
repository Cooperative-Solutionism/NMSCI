-- Fix: prevent duplicate block heights and multiple children for the same parent block.
-- Database: PostgreSQL
--
-- Run the duplicate checks first. If either query returns rows, resolve the
-- duplicate block records manually before adding the unique constraints.

select height, count(*) as cnt
from block_infos
group by height
having count(*) > 1;

select previous_block_hash, count(*) as cnt
from block_infos
group by previous_block_hash
having count(*) > 1;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'uk_block_infos_height'
    ) then
        alter table block_infos
            add constraint uk_block_infos_height
            unique (height);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'uk_block_infos_previous_block_hash'
    ) then
        alter table block_infos
            add constraint uk_block_infos_previous_block_hash
            unique (previous_block_hash);
    end if;
end $$;

select conname
from pg_constraint
where conname in (
    'uk_block_infos_height',
    'uk_block_infos_previous_block_hash'
);
