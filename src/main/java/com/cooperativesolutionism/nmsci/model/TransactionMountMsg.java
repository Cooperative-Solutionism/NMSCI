package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.serializer.BytesToHexSerializer;
import com.cooperativesolutionism.nmsci.serializer.IntToHexSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("交易挂载信息")
@Entity
@Table(name = "transaction_mount_msgs")
public class TransactionMountMsg implements Message {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("5")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("挂载的交易记录信息的id")
    @Column(name = "mounted_transaction_record_id", nullable = false)
    private UUID mountedTransactionRecordId;

    @Comment("交易难度目标")
    @Column(name = "transaction_difficulty_target", nullable = false)
    @JsonSerialize(using = IntToHexSerializer.class)
    private Integer transactionDifficultyTarget;

    @Comment("随机数")
    @Column(name = "nonce", nullable = false)
    private Integer nonce;

    @Comment("消费节点公钥")
    @Column(name = "consume_node_pubkey", nullable = false)
    @ByteArraySize(33)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] consumeNodePubkey;

    @Comment("流转节点公钥")
    @Column(name = "flow_node_pubkey", nullable = false)
    @ByteArraySize(33)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] flowNodePubkey;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    @ByteArraySize(33)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] centralPubkey;

    @Comment("消费节点签名")
    @Column(name = "consume_node_signature", nullable = false)
    @ByteArraySize(64)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] consumeNodeSignature;

    @Comment("流转节点签名")
    @Column(name = "flow_node_signature", nullable = false)
    @ByteArraySize(64)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] flowNodeSignature;

    @Comment("信息确认时间，单位微秒，时区UTC+0")
    @Column(name = "confirm_timestamp", nullable = false)
    private Long confirmTimestamp;

    @Comment("中心签名")
    @Column(name = "central_signature", nullable = false)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] centralSignature;

    @Comment("原始字节格式")
    @Column(name = "raw_bytes", nullable = false)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] rawBytes;

    @Comment("信息的dblsha256hash_reverse")
    @Column(name = "txid", nullable = false)
    @JsonSerialize(using = BytesToHexSerializer.class)
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

    public UUID getMountedTransactionRecordId() {
        return mountedTransactionRecordId;
    }

    public void setMountedTransactionRecordId(UUID mountedTransactionRecordId) {
        this.mountedTransactionRecordId = mountedTransactionRecordId;
    }

    public Integer getTransactionDifficultyTarget() {
        return transactionDifficultyTarget;
    }

    public void setTransactionDifficultyTarget(Integer transactionDifficultyTarget) {
        this.transactionDifficultyTarget = transactionDifficultyTarget;
    }

    public Integer getNonce() {
        return nonce;
    }

    public void setNonce(Integer nonce) {
        this.nonce = nonce;
    }

    public byte[] getConsumeNodePubkey() {
        return consumeNodePubkey;
    }

    public void setConsumeNodePubkey(byte[] consumeNodePubkey) {
        this.consumeNodePubkey = consumeNodePubkey;
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

    public byte[] getConsumeNodeSignature() {
        return consumeNodeSignature;
    }

    public void setConsumeNodeSignature(byte[] consumeNodeSignature) {
        this.consumeNodeSignature = consumeNodeSignature;
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