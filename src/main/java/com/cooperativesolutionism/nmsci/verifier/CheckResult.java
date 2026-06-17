package com.cooperativesolutionism.nmsci.verifier;

/**
 * 单个验证项的结果。
 *
 * @param name     验证项名称（中文短描述）
 * @param category 分类
 * @param status   通过 / 失败 / 跳过（信息性，无期望值时）
 * @param detail   说明：失败原因、关键数值或跳过理由
 */
public record CheckResult(String name, CheckCategory category, Status status, String detail) {

    public enum Status {
        PASSED,
        FAILED,
        SKIPPED
    }

    public static CheckResult passed(String name, CheckCategory category, String detail) {
        return new CheckResult(name, category, Status.PASSED, detail);
    }

    public static CheckResult failed(String name, CheckCategory category, String detail) {
        return new CheckResult(name, category, Status.FAILED, detail);
    }

    public static CheckResult skipped(String name, CheckCategory category, String detail) {
        return new CheckResult(name, category, Status.SKIPPED, detail);
    }

    public boolean isFailure() {
        return status == Status.FAILED;
    }
}
