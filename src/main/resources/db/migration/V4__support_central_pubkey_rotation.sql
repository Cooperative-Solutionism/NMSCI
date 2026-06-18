-- 中心公钥轮换支持（评审 #1，Option A）：将公证唯一性从「每流转节点仅一次」放宽为
-- 「每 (流转节点公钥, 中心公钥) 一次」，使中心公钥被冻结/轮换后，流转节点可对新的中心公钥重新授权，
-- 保持节点连续性。对齐 PROTOCOL.md 第15/68行的轮换恢复语义；不改任何字节协议/区块格式/版本号。

alter table central_pubkey_empower_msgs
    drop constraint uk_central_pubkey_empower_msgs_flow_node_pubkey;

alter table central_pubkey_empower_msgs
    add constraint uk_central_pubkey_empower_flow_central
        unique (flow_node_pubkey, central_pubkey);

-- 新组合唯一约束已自带 (flow_node_pubkey, central_pubkey) 等价索引，删除冗余的显式索引。
drop index idx_central_pubkey_empower_flow_central;
