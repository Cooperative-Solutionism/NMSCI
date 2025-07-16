package com.cooperativesolutionism.nmsci.model;

import com.cooperativesolutionism.nmsci.serializer.IdentifiableToStringSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("消费链的边")
@Entity
@Table(name = "consume_chain_edges")
public class ConsumeChainEdge implements Identifiable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("边的起点")
    @JoinColumn(name = "source", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private FlowNodeRegisterMsg source;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("边的终点")
    @JoinColumn(name = "target", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private FlowNodeRegisterMsg target;

    @NotNull
    @Comment("金额")
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotNull
    @Comment("货币类型")
    @Column(name = "currency_type", nullable = false)
    private Short currencyType;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("边所属的消费链")
    @JoinColumn(name = "chain", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private ConsumeChain chain;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("关联的交易记录")
    @JoinColumn(name = "related_transaction_record", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private TransactionRecordMsg relatedTransactionRecord;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("关联的交易挂载")
    @JoinColumn(name = "related_transaction_mount", nullable = false)
    @JsonSerialize(using = IdentifiableToStringSerializer.class)
    private TransactionMountMsg relatedTransactionMount;

    @NotNull
    @Comment("关联的交易挂载的确认时间，单位微秒，时区UTC+0")
    @Column(name = "related_transaction_mount_timestamp", nullable = false)
    private Long relatedTransactionMountTimestamp;

    @NotNull
    @Comment("所属的消费链是否已成环")
    @ColumnDefault("false")
    @Column(name = "is_loop", nullable = false)
    private Boolean isLoop = false;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FlowNodeRegisterMsg getSource() {
        return source;
    }

    public void setSource(FlowNodeRegisterMsg source) {
        this.source = source;
    }

    public FlowNodeRegisterMsg getTarget() {
        return target;
    }

    public void setTarget(FlowNodeRegisterMsg target) {
        this.target = target;
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

    public ConsumeChain getChain() {
        return chain;
    }

    public void setChain(ConsumeChain chain) {
        this.chain = chain;
    }

    public TransactionRecordMsg getRelatedTransactionRecord() {
        return relatedTransactionRecord;
    }

    public void setRelatedTransactionRecord(TransactionRecordMsg relatedTransactionRecord) {
        this.relatedTransactionRecord = relatedTransactionRecord;
    }

    public TransactionMountMsg getRelatedTransactionMount() {
        return relatedTransactionMount;
    }

    public void setRelatedTransactionMount(TransactionMountMsg relatedTransactionMount) {
        this.relatedTransactionMount = relatedTransactionMount;
    }

    public Long getRelatedTransactionMountTimestamp() {
        return relatedTransactionMountTimestamp;
    }

    public void setRelatedTransactionMountTimestamp(Long relatedTransactionMountTimestamp) {
        this.relatedTransactionMountTimestamp = relatedTransactionMountTimestamp;
    }

    public Boolean getIsLoop() {
        return isLoop;
    }

    public void setIsLoop(Boolean isLoop) {
        this.isLoop = isLoop;
    }

}