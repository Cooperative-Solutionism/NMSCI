package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.serializer.BytesToHexSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("中心公钥冻结信息")
@Entity
@Table(name = "central_pubkey_locked_msgs")
public class CentralPubkeyLockedMsg implements Message {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("2")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    @ByteArraySize(33)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] centralPubkey;

    @Comment("中心对前三项数据的预确认签名")
    @Column(name = "central_signature_pre", nullable = false)
    @ByteArraySize(64)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] centralSignaturePre;

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

    public byte[] getCentralPubkey() {
        return centralPubkey;
    }

    public void setCentralPubkey(byte[] centralPubkey) {
        this.centralPubkey = centralPubkey;
    }

    public byte[] getCentralSignaturePre() {
        return centralSignaturePre;
    }

    public void setCentralSignaturePre(byte[] centralSignaturePre) {
        this.centralSignaturePre = centralSignaturePre;
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