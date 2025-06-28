create table central_pubkey_empower_msgs
(
    id                  uuid               not null
        primary key,
    msg_type            smallint default 0 not null,
    flow_node_pubkey    bytea              not null,
    central_pubkey      bytea              not null,
    flow_node_signature bytea              not null,
    confirm_timestamp   bigint             not null,
    central_signature   bytea              not null
);

comment on table central_pubkey_empower_msgs is '中心公钥公证信息';

comment on column central_pubkey_empower_msgs.msg_type is '信息类型';

comment on column central_pubkey_empower_msgs.flow_node_pubkey is '流转节点公钥';

comment on column central_pubkey_empower_msgs.central_pubkey is '中心公钥';

comment on column central_pubkey_empower_msgs.flow_node_signature is '流转节点签名';

comment on column central_pubkey_empower_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column central_pubkey_empower_msgs.central_signature is '中心签名';

alter table central_pubkey_empower_msgs
    owner to postgres;

create table central_pubkey_locked_msgs
(
    id                    uuid               not null
        primary key,
    msg_type              smallint default 1 not null,
    central_pubkey        bytea              not null,
    central_signature_pre bytea              not null,
    confirm_timestamp     bigint             not null,
    central_signature     bytea              not null
);

comment on table central_pubkey_locked_msgs is '中心公钥冻结信息';

comment on column central_pubkey_locked_msgs.msg_type is '信息类型';

comment on column central_pubkey_locked_msgs.central_pubkey is '中心公钥';

comment on column central_pubkey_locked_msgs.central_signature_pre is '中心对前三项数据的预确认签名';

comment on column central_pubkey_locked_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column central_pubkey_locked_msgs.central_signature is '中心签名';

alter table central_pubkey_locked_msgs
    owner to postgres;

create table flow_node_register_msgs
(
    id                         uuid               not null
        primary key,
    msg_type                   smallint default 2 not null,
    register_difficulty_target integer            not null,
    nonce                      integer            not null,
    flow_node_pubkey           bytea              not null,
    central_pubkey             bytea              not null,
    flow_node_signature        bytea              not null,
    confirm_timestamp          bigint             not null,
    central_signature          bytea              not null
);

comment on table flow_node_register_msgs is '流转节点注册信息';

comment on column flow_node_register_msgs.msg_type is '信息类型';

comment on column flow_node_register_msgs.register_difficulty_target is '注册难度目标';

comment on column flow_node_register_msgs.nonce is '随机数';

comment on column flow_node_register_msgs.flow_node_pubkey is '流转节点公钥';

comment on column flow_node_register_msgs.central_pubkey is '中心公钥';

comment on column flow_node_register_msgs.flow_node_signature is '流转节点签名';

comment on column flow_node_register_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column flow_node_register_msgs.central_signature is '中心签名';

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
    central_signature   bytea              not null
);

comment on table flow_node_locked_msgs is '流转节点冻结信息';

comment on column flow_node_locked_msgs.msg_type is '信息类型';

comment on column flow_node_locked_msgs.flow_node_pubkey is '流转节点公钥';

comment on column flow_node_locked_msgs.central_pubkey is '中心公钥';

comment on column flow_node_locked_msgs.flow_node_signature is '流转节点签名';

comment on column flow_node_locked_msgs.confirm_timestamp is '信息确认时间，单位微秒，时区UTC+0';

comment on column flow_node_locked_msgs.central_signature is '中心签名';

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
    central_signature             bytea              not null
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
    central_signature             bytea              not null
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

alter table transaction_mount_msgs
    owner to postgres;

