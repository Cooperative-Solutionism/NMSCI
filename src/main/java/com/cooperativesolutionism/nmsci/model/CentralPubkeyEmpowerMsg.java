package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("中心公钥公证信息")
@Entity
@Table(name = "central_pubkey_empower_msgs")
public class CentralPubkeyEmpowerMsg {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("0")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("流转节点公钥")
    @Column(name = "flow_node_pubkey", nullable = false)
    @ByteArraySize(33)
    private byte[] flowNodePubkey;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    @ByteArraySize(33)
    private byte[] centralPubkey;

    @Comment("流转节点签名")
    @Column(name = "flow_node_signature", nullable = false)
    @ByteArraySize(64)
    private byte[] flowNodeSignature;

    @Comment("信息确认时间，单位微秒，时区UTC+0")
    @Column(name = "confirm_timestamp", nullable = false)
    private Long confirmTimestamp;

    @Comment("中心签名")
    @Column(name = "central_signature", nullable = false)
    private byte[] centralSignature;

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

    public byte[] getFlowNodePubkey() {
        return flowNodePubkey;
    }

    public void setFlowNodePubkey(byte[] flowNodePubkey) {
        this.flowNodePubkey = flowNodePubkey;
    }

    public byte[] getCentralPubkey() {
        return centralPubkey;
    }

    public void setCentralPubkey(byte[] centralPubkey) {
        this.centralPubkey = centralPubkey;
    }

    public byte[] getFlowNodeSignature() {
        return flowNodeSignature;
    }

    public void setFlowNodeSignature(byte[] flowNodeSignature) {
        this.flowNodeSignature = flowNodeSignature;
    }

    public Long getConfirmTimestamp() {
        return confirmTimestamp;
    }

    public void setConfirmTimestamp(Long confirmTimestamp) {
        this.confirmTimestamp = confirmTimestamp;
    }

    public byte[] getCentralSignature() {
        return centralSignature;
    }

    public void setCentralSignature(byte[] centralSignature) {
        this.centralSignature = centralSignature;
    }

}