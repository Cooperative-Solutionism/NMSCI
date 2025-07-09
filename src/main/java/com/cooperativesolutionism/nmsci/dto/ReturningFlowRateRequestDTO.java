package com.cooperativesolutionism.nmsci.dto;

public class ReturningFlowRateRequestDTO {

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
