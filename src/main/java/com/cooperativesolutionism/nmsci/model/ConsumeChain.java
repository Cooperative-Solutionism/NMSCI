package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.serializer.IdentifiableToStringSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("消费链")
@Entity
@Table(name = "consume_chains")
public class ConsumeChain implements Identifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("消费链起点")
    @JoinColumn(name = "start", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private FlowNodeRegisterMsg start;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("消费链终点")
    @JoinColumn(name = "\"end\"", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private FlowNodeRegisterMsg end;

    @NotNull
    @Comment("金额")
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotNull
    @Comment("货币类型")
    @Column(name = "currency_type", nullable = false)
    private Short currencyType;

    @NotNull
    @Comment("消费链是否已成环")
    @ColumnDefault("false")
    @Column(name = "is_loop", nullable = false)
    private Boolean isLoop = false;

    @NotNull
    @Comment("链尾挂载时间，单位微秒，时区UTC+0")
    @Column(name = "tail_mount_timestamp", nullable = false)
    private Long tailMountTimestamp;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FlowNodeRegisterMsg getStart() {
        return start;
    }

    public void setStart(FlowNodeRegisterMsg start) {
        this.start = start;
    }

    public FlowNodeRegisterMsg getEnd() {
        return end;
    }

    public void setEnd(FlowNodeRegisterMsg end) {
        this.end = end;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public Short getCurrencyType() {
        return currencyType;
    }

    public void setCurrencyType(Short currencyType) {
        this.currencyType = currencyType;
    }

    public Boolean getIsLoop() {
        return isLoop;
    }

    public void setIsLoop(Boolean isLoop) {
        this.isLoop = isLoop;
    }

    public Long getTailMountTimestamp() {
        return tailMountTimestamp;
    }

    public void setTailMountTimestamp(Long tailMountTimestamp) {
        this.tailMountTimestamp = tailMountTimestamp;
    }

}