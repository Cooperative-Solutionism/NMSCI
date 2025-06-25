package com.cooperativesolutionism.nmsci.model;

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
public class FlowNodeRegisterMsg {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("2")
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
    private byte[] flowNodePubkey;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    private byte[] centralPubkey;

    @Comment("流转节点签名")
    @Column(name = "flow_node_signature", nullable = false)
    private byte[] flowNodeSignature;

    @Comment("信息确认时间，单位微秒，时区UTC+8")
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