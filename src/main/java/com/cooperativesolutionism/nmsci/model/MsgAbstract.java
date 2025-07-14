package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.serializer.BytesToHexSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("所有类型的msg的部分字段摘要")
@Entity
@Table(name = "msg_abstracts")
public class MsgAbstract {
    @Id
    @Comment("msg的msg_type与id的拼接")
    @Column(name = "id", nullable = false)
    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] id;

    @Comment("信息类型")
    @Column(name = "msg_type", nullable = false)
    private Short msgType;

    @Comment("信息id")
    @Column(name = "msg_id", nullable = false)
    private UUID msgId;

    @Comment("信息确认时间，单位微秒，时区UTC+0")
    @Column(name = "confirm_timestamp", nullable = false)
    private Long confirmTimestamp;

    @Comment("是否已被装入区块")
    @ColumnDefault("false")
    @Column(name = "is_in_block", nullable = false)
    private Boolean isInBlock = false;

    public byte[] getId() {
        return id;
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public Short getMsgType() {
        return msgType;
    }

    public void setMsgType(Short msgType) {
        this.msgType = msgType;
    }

    public UUID getMsgId() {
        return msgId;
    }

    public void setMsgId(UUID msgId) {
        this.msgId = msgId;
    }

    public Long getConfirmTimestamp() {
        return confirmTimestamp;
    }

    public void setConfirmTimestamp(Long confirmTimestamp) {
        this.confirmTimestamp = confirmTimestamp;
    }

    public Boolean getIsInBlock() {
        return isInBlock;
    }

    public void setIsInBlock(Boolean isInBlock) {
        this.isInBlock = isInBlock;
    }

}