package com.cooperativesolutionism.nmsci.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Comment;

import java.util.UUID;

@Comment("消费链的边")
@Entity
@Table(name = "consume_chain_edges")
public class ConsumeChainEdge {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("边的起点")
    @JoinColumn(name = "source", nullable = false)
    private FlowNodeRegisterMsg source;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Comment("边的终点")
    @JoinColumn(name = "target", nullable = false)
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
    private ConsumeChain chain;

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

}