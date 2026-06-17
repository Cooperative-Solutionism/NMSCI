package com.cooperativesolutionism.nmsci.model;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.serializer.BytesToHexSerializer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("中心公钥冻结信息")
@Entity
@Table(
        name = "central_pubkey_locked_msgs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_central_pubkey_locked_msgs_central_pubkey",
                        columnNames = "central_pubkey"
                ),
                @UniqueConstraint(
                        name = "uk_central_pubkey_locked_msgs_txid",
                        columnNames = "txid"
                )
        }
)
public class CentralPubkeyLockedMsg implements CentrallySignedMessage {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Comment("信息类型")
    @ColumnDefault("2")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    @ByteArraySize(COMPRESSED_PUBLIC_KEY_BYTES)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] centralPubkey;

    @Comment("中心对前三项数据的预确认签名")
    @Column(name = "central_signature_pre", nullable = false)
    @ByteArraySize(RS_SIGNATURE_BYTES)
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
    @JsonIgnore
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CentralPubkeyLockedMsg)) {
            return false;
        }
        CentralPubkeyLockedMsg that = (CentralPubkeyLockedMsg) o;
        return id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return CentralPubkeyLockedMsg.class.hashCode();
    }

}
