package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.serializer.HexSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("流转节点注册信息")
@Entity
@Table(name = "flow_node_register_msgs")
public class FlowNodeRegisterMsg implements Message {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("0")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("注册难度目标")
    @Column(name = "register_difficulty_target", nullable = false)
    private Integer registerDifficultyTarget;

    @Comment("随机数")
    @Column(name = "nonce", nullable = false)
    private Integer nonce;

    @Comment("流转节点公钥")
    @Column(name = "flow_node_pubkey", nullable = false)
    @ByteArraySize(33)
    @JsonSerialize(using = HexSerializer.class)
    private byte[] flowNodePubkey;

    @Comment("流转节点签名")
    @Column(name = "flow_node_signature", nullable = false)
    @ByteArraySize(64)
    @JsonSerialize(using = HexSerializer.class)
    private byte[] flowNodeSignature;

    @Comment("原始字节格式")
    @Column(name = "raw_bytes", nullable = false)
    @JsonSerialize(using = HexSerializer.class)
    private byte[] rawBytes;

    @Comment("信息的dblsha256hash_reverse")
    @Column(name = "txid", nullable = false)
    @JsonSerialize(using = HexSerializer.class)
    private byte[] txid;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Short getMsgType() {
        return msgType;
    }

    public void setMsgType(Short msgType) {
        this.msgType = msgType;
    }

    public Integer getRegisterDifficultyTarget() {
        return registerDifficultyTarget;
    }

    public void setRegisterDifficultyTarget(Integer registerDifficultyTarget) {
        this.registerDifficultyTarget = registerDifficultyTarget;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public byte[] getFlowNodePubkey() {
        return flowNodePubkey;
    }

    public void setFlowNodePubkey(byte[] flowNodePubkey) {
        this.flowNodePubkey = flowNodePubkey;
    }

    public byte[] getFlowNodeSignature() {
        return flowNodeSignature;
    }

    public void setFlowNodeSignature(byte[] flowNodeSignature) {
        this.flowNodeSignature = flowNodeSignature;
    }

    public Long getConfirmTimestamp() {
        return 0L;
    }

    public void setConfirmTimestamp(Long confirmTimestamp) {
    }

    public byte[] getRawBytes() {
        return rawBytes;
    }

    public void setRawBytes(byte[] rawBytes) {
        this.rawBytes = rawBytes;
    }

    public byte[] getTxid() {
        return txid;
    }

    public void setTxid(byte[] txid) {
        this.txid = txid;
    }

}