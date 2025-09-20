create table central_pubkey_empower_msgs
(
    id                  uuid               not null
        primary key,
    msg_type            smallint default 1 not null,
    flow_node_pubkey    bytea              not null,
    central_pubkey      bytea              not null,
    flow_node_signature bytea              not null,
    confirm_timestamp   bigint             not null,
    central_signature   bytea              not null,
    raw_bytes           bytea              not null,
    txid                bytea              not null
);

comment on table central_pubkey_empower_msgs is '中心公钥公证信息';

comment on column central_pubkey_empower_msgs.msg_type is '信息类型';

comment on column central_pubkey_empower_msgs.flow_node_pubkey is '流转节点公钥';

comment on column central_pubkey_empower_msgs.central_pubkey is '中心公钥';

comment on column central_pubkey_empower_msgs.flow_node_signature is '流转节点签名';

comment on column central_pubkey_empower_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column central_pubkey_empower_msgs.central_signature is '中心签名';

comment on column central_pubkey_empower_msgs.raw_bytes is '原始字节格式';

comment on column central_pubkey_empower_msgs.txid is '信息的dblsha256hash_reverse';

alter table central_pubkey_empower_msgs
    owner to postgres;

create table central_pubkey_locked_msgs
(
    id                    uuid               not null
        primary key,
    msg_type              smallint default 2 not null,
    central_pubkey        bytea              not null,
    central_signature_pre bytea              not null,
    confirm_timestamp     bigint             not null,
    central_signature     bytea              not null,
    raw_bytes             bytea              not null,
    txid                  bytea              not null
);

comment on table central_pubkey_locked_msgs is '中心公钥冻结信息';

comment on column central_pubkey_locked_msgs.msg_type is '信息类型';

comment on column central_pubkey_locked_msgs.central_pubkey is '中心公钥';

comment on column central_pubkey_locked_msgs.central_signature_pre is '中心对前三项数据的预确认签名';

comment on column central_pubkey_locked_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column central_pubkey_locked_msgs.central_signature is '中心签名';

comment on column central_pubkey_locked_msgs.raw_bytes is '原始字节格式';

comment on column central_pubkey_locked_msgs.txid is '信息的dblsha256hash_reverse';

alter table central_pubkey_locked_msgs
    owner to postgres;

create table flow_node_register_msgs
(
    id                         uuid               not null
        primary key,
    msg_type                   smallint default 0 not null,
    register_difficulty_target integer            not null,
    nonce                      integer            not null,
    flow_node_pubkey           bytea              not null,
    flow_node_signature        bytea              not null,
    raw_bytes                  bytea              not null,
    txid                       bytea              not null
);

comment on table flow_node_register_msgs is '流转节点注册信息';

comment on column flow_node_register_msgs.msg_type is '信息类型';

comment on column flow_node_register_msgs.register_difficulty_target is '注册难度目标';

comment on column flow_node_register_msgs.nonce is '随机数';

comment on column flow_node_register_msgs.flow_node_pubkey is '流转节点公钥';

comment on column flow_node_register_msgs.flow_node_signature is '流转节点签名';

comment on column flow_node_register_msgs.raw_bytes is '原始字节格式';

comment on column flow_node_register_msgs.txid is '信息的dblsha256hash_reverse';

alter table flow_node_register_msgs
    owner to postgres;

create table flow_node_locked_msgs
(
    id                  uuid               not null
        primary key,
    msg_type            smallint default 3 not null,
    flow_node_pubkey    bytea              not null,
    central_pubkey      bytea              not null,
    flow_node_signature bytea              not null,
    confirm_timestamp   bigint             not null,
    central_signature   bytea              not null,
    raw_bytes           bytea              not null,
    txid                bytea              not null
);

comment on table flow_node_locked_msgs is '流转节点冻结信息';

comment on column flow_node_locked_msgs.msg_type is '信息类型';

comment on column flow_node_locked_msgs.flow_node_pubkey is '流转节点公钥';

comment on column flow_node_locked_msgs.central_pubkey is '中心公钥';

comment on column flow_node_locked_msgs.flow_node_signature is '流转节点签名';

comment on column flow_node_locked_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column flow_node_locked_msgs.central_signature is '中心签名';

comment on column flow_node_locked_msgs.raw_bytes is '原始字节格式';

comment on column flow_node_locked_msgs.txid is '信息的dblsha256hash_reverse';

alter table flow_node_locked_msgs
    owner to postgres;

create table transaction_record_msgs
(
    id                            uuid               not null
        primary key,
    msg_type                      smallint default 4 not null,
    amount                        bigint             not null,
    currency_type                 smallint           not null,
    transaction_difficulty_target integer            not null,
    nonce                         integer            not null,
    consume_node_pubkey           bytea              not null,
    flow_node_pubkey              bytea              not null,
    central_pubkey                bytea              not null,
    consume_node_signature        bytea              not null,
    flow_node_signature           bytea              not null,
    confirm_timestamp             bigint             not null,
    central_signature             bytea              not null,
    raw_bytes                     bytea              not null,
    txid                          bytea              not null
);

comment on table transaction_record_msgs is '交易记录信息';

comment on column transaction_record_msgs.msg_type is '信息类型';

comment on column transaction_record_msgs.amount is '金额';

comment on column transaction_record_msgs.currency_type is '货币类型';

comment on column transaction_record_msgs.transaction_difficulty_target is '交易难度目标';

comment on column transaction_record_msgs.nonce is '随机数';

comment on column transaction_record_msgs.consume_node_pubkey is '消费节点公钥';

comment on column transaction_record_msgs.flow_node_pubkey is '流转节点公钥';

comment on column transaction_record_msgs.central_pubkey is '中心公钥';

comment on column transaction_record_msgs.consume_node_signature is '消费节点签名';

comment on column transaction_record_msgs.flow_node_signature is '流转节点签名';

comment on column transaction_record_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column transaction_record_msgs.central_signature is '中心签名';

comment on column transaction_record_msgs.raw_bytes is '原始字节格式';

comment on column transaction_record_msgs.txid is '信息的dblsha256hash_reverse';

alter table transaction_record_msgs
    owner to postgres;

create table transaction_mount_msgs
(
    id                            uuid               not null
        primary key,
    msg_type                      smallint default 5 not null,
    mounted_transaction_record_id uuid               not null,
    transaction_difficulty_target integer            not null,
    nonce                         integer            not null,
    consume_node_pubkey           bytea              not null,
    flow_node_pubkey              bytea              not null,
    central_pubkey                bytea              not null,
    consume_node_signature        bytea              not null,
    flow_node_signature           bytea              not null,
    confirm_timestamp             bigint             not null,
    central_signature             bytea              not null,
    raw_bytes                     bytea              not null,
    txid                          bytea              not null
);

comment on table transaction_mount_msgs is '交易挂载信息';

comment on column transaction_mount_msgs.msg_type is '信息类型';

comment on column transaction_mount_msgs.mounted_transaction_record_id is '挂载的交易记录信息的id';

comment on column transaction_mount_msgs.transaction_difficulty_target is '交易难度目标';

comment on column transaction_mount_msgs.nonce is '随机数';

comment on column transaction_mount_msgs.consume_node_pubkey is '消费节点公钥';

comment on column transaction_mount_msgs.flow_node_pubkey is '流转节点公钥';

comment on column transaction_mount_msgs.central_pubkey is '中心公钥';

comment on column transaction_mount_msgs.consume_node_signature is '消费节点签名';

comment on column transaction_mount_msgs.flow_node_signature is '流转节点签名';

comment on column transaction_mount_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column transaction_mount_msgs.central_signature is '中心签名';

comment on column transaction_mount_msgs.raw_bytes is '原始字节格式';

comment on column transaction_mount_msgs.txid is '信息的dblsha256hash_reverse';

alter table transaction_mount_msgs
    owner to postgres;

create table block_infos
(
    id                            bytea             not null
        constraint block_info_pkey
            primary key,
    version                       integer default 1 not null,
    height                        bigint            not null,
    source_code_zip_hash          bytea             not null,
    previous_block_hash           bytea             not null,
    merkle_root                   bytea             not null,
    max_msg_timestamp             bigint            not null,
    register_difficulty_target    integer           not null,
    transaction_difficulty_target integer           not null,
    central_pubkey                bytea             not null,
    timestamp                     bigint            not null,
    central_signature             bytea             not null,
    dat_filepath                  text              not null,
    source_code_zip_filepath      text              not null,
    raw_bytes                     bytea             not null
);

comment on table block_infos is '区块信息';

comment on column block_infos.id is '本区块头的dblsha256hash';

comment on column block_infos.height is '区块高度';

comment on column block_infos.source_code_zip_hash is '相应版本全代码压缩包(包含协议内容)的sha256hash';

comment on column block_infos.previous_block_hash is '前区块头的dblsha256hash';

comment on column block_infos.merkle_root is '所有信息的默克尔根';

comment on column block_infos.max_msg_timestamp is '信息内的最大时间戳，单位微秒，时区UTC+0';

comment on column block_infos.register_difficulty_target is '注册难度目标';

comment on column block_infos.transaction_difficulty_target is '交易难度目标';

comment on column block_infos.central_pubkey is '中心公钥';

comment on column block_infos.timestamp is '区块固定时间，单位微秒，时区UTC+0';

comment on column block_infos.central_signature is '中心签名';

comment on column block_infos.dat_filepath is '保存区块的dat文件的文件路径';

comment on column block_infos.source_code_zip_filepath is '相应版本全代码(包含协议文本)压缩包的文件路径';

comment on column block_infos.raw_bytes is '原始字节格式';

alter table block_infos
    owner to postgres;

create table msg_abstracts
(
    id                bytea                 not null
        constraint all_type_msg_abstracts_pkey
            primary key,
    msg_type          smallint              not null,
    msg_id            uuid                  not null,
    confirm_timestamp bigint                not null,
    is_in_block       boolean default false not null
);

comment on table msg_abstracts is '所有类型的msg的部分字段摘要';

comment on column msg_abstracts.id is 'msg的msg_type与id的拼接';

comment on column msg_abstracts.msg_type is '信息类型';

comment on column msg_abstracts.msg_id is '信息id';

comment on column msg_abstracts.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column msg_abstracts.is_in_block is '是否已被装入区块';

alter table msg_abstracts
    owner to postgres;

create table consume_chains
(
    id                   uuid                  not null
        primary key,
    start                uuid                  not null
        constraint fk_start_flow_node
            references flow_node_register_msgs,
    "end"                uuid                  not null
        constraint fk_end_flow_node
            references flow_node_register_msgs,
    amount               bigint                not null,
    currency_type        smallint              not null,
    is_loop              boolean default false not null,
    tail_mount_timestamp bigint                not null
);

comment on table consume_chains is '消费链';

comment on column consume_chains.start is '消费链起点';

comment on column consume_chains."end" is '消费链终点';

comment on column consume_chains.amount is '金额';

comment on column consume_chains.currency_type is '货币类型';

comment on column consume_chains.is_loop is '消费链是否已成环';

comment on column consume_chains.tail_mount_timestamp is '链尾挂载时间，单位微秒，时区UTC+0';

alter table consume_chains
    owner to postgres;

create table consume_chain_edges
(
    id                                  uuid                  not null
        primary key,
    source                              uuid                  not null
        constraint fk_source_flow_node
            references flow_node_register_msgs,
    target                              uuid                  not null
        constraint fk_target_flow_node
            references flow_node_register_msgs,
    amount                              bigint                not null,
    currency_type                       smallint              not null,
    chain                               uuid                  not null
        constraint fk_chain_consume_chain
            references consume_chains,
    related_transaction_record          uuid                  not null
        constraint fk_related_transaction_record
            references transaction_record_msgs,
    related_transaction_mount           uuid                  not null
        constraint fk_related_transaction_mount
            references transaction_mount_msgs,
    related_transaction_mount_timestamp bigint                not null,
    is_loop                             boolean default false not null
);

comment on table consume_chain_edges is '消费链的边';

comment on column consume_chain_edges.source is '边的起点';

comment on column consume_chain_edges.target is '边的终点';

comment on column consume_chain_edges.amount is '金额';

comment on column consume_chain_edges.currency_type is '货币类型';

comment on column consume_chain_edges.chain is '边所属的消费链';

comment on column consume_chain_edges.related_transaction_record is '关联的交易记录';

comment on column consume_chain_edges.related_transaction_mount is '关联的交易挂载';

comment on column consume_chain_edges.related_transaction_mount_timestamp is '关联的交易挂载的确认时间，单位微秒，时区UTC+0';

comment on column consume_chain_edges.is_loop is '所属的消费链是否已成环';

alter table consume_chain_edges
    owner to postgres;

