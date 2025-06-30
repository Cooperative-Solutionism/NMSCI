package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
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
    @ByteArraySize(33)
    private byte[] centralPubkey;

    @Comment("中心对前三项数据的预确认签名")
    @Column(name = "central_signature_pre", nullable = false)
    @ByteArraySize(64)
    private byte[] centralSignaturePre;

    @Comment("信息确认时间，单位微秒，时区UTC+0")
    @Column(name = "confirm_timestamp", nullable = false)
    private Long confirmTimestamp;

    @Comment("中心签名")
    @Column(name = "central_signature", nullable = false)
    private byte[] centralSignature;

    @Comment("是否已被装入区块")
    @ColumnDefault("false")
    @Column(name = "is_in_block", nullable = false)
    private Boolean isInBlock = false;

    public Boolean getIsInBlock() {
        return isInBlock;
    }

    public void setIsInBlock(Boolean isInBlock) {
        this.isInBlock = isInBlock;
    }

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