package com.cooperativesolutionism.nmsci.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("中心公钥冻结信息")
@Entity
@Table(name = "central_pubkey_locked_msgs")
public class CentralPubkeyLockedMsg {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("1")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    private byte[] centralPubkey;

    @Comment("中心对前三项数据的预确认签名")
    @Column(name = "central_signature_pre", nullable = false)
    private byte[] centralSignaturePre;

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

}