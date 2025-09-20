package com.cooperativesolutionism.nmsci.dto;

import java.util.UUID;

public class ReturningFlowRateRequestDTO {

    /**
     * 查询回流率时的源流转节点ID
     */
    private UUID sourceId;

    /**
     * 查询回流率时的目标流转节点ID
     */
    private UUID targetId;

    /**
     * 查询回流率时的源流转节点公钥
     */
    private byte[] source;

    /**
     * 查询回流率时的目标流转节点公钥
     */
    private byte[] target;

    /**
     * 查询回流率时的开始时间
     */
    private long startTime;

    /**
     * 查询回流率时的结束时间
     */
    private long endTime;

    /**
     * 查询回流率时的货币类型
     */
    private short currencyType;

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public byte[] getSource() {
        return source;
    }

    public void setSource(byte[] source) {
        this.source = source;
    }

    public byte[] getTarget() {
        return target;
    }

    public void setTarget(byte[] target) {
        this.target = target;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public short getCurrencyType() {
        return currencyType;
    }

    public void setCurrencyType(short currencyType) {
        this.currencyType = currencyType;
    }

}
