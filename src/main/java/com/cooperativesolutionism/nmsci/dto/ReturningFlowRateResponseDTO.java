package com.cooperativesolutionism.nmsci.dto;

public class ReturningFlowRateResponseDTO {

    /**
     * 回流率
     */
    private double returningFlowRate;

    /**
     * 已成环的金额总和
     */
    private double loopedAmount;

    /**
     * 未成环的金额总和(滞留指数)
     */
    private double unloopedAmount;

    /**
     * 目标(target)所有未成环的金额总和(总滞留指数)
     */
    private double targetTotalUnloopedAmount;

    /**
     * 货币类型
     */
    private short currencyType;

    public ReturningFlowRateResponseDTO(double returningFlowRate, double loopedAmount, double unloopedAmount, double targetTotalUnloopedAmount, short currencyType) {
        this.returningFlowRate = returningFlowRate;
        this.loopedAmount = loopedAmount;
        this.unloopedAmount = unloopedAmount;
        this.targetTotalUnloopedAmount = targetTotalUnloopedAmount;
        this.currencyType = currencyType;
    }

    public double getReturningFlowRate() {
        return returningFlowRate;
    }

    public void setReturningFlowRate(double returningFlowRate) {
        this.returningFlowRate = returningFlowRate;
    }

    public double getLoopedAmount() {
        return loopedAmount;
    }

    public void setLoopedAmount(double loopedAmount) {
        this.loopedAmount = loopedAmount;
    }

    public double getUnloopedAmount() {
        return unloopedAmount;
    }

    public void setUnloopedAmount(double unloopedAmount) {
        this.unloopedAmount = unloopedAmount;
    }

    public short getCurrencyType() {
        return currencyType;
    }

    public void setCurrencyType(short currencyType) {
        this.currencyType = currencyType;
    }

    public double getTargetTotalUnloopedAmount() {
        return targetTotalUnloopedAmount;
    }

    public void setTargetTotalUnloopedAmount(double targetTotalUnloopedAmount) {
        this.targetTotalUnloopedAmount = targetTotalUnloopedAmount;
    }
}
