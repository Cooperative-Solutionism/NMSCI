package com.cooperativesolutionism.nmsci.verifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 整条链的验证结果：链级验证项（解析、有状态回放、汇总）+ 各区块验证项。
 */
public final class ChainVerificationResult {

    private final boolean parseSucceeded;
    private final List<CheckResult> chainChecks;
    private final List<BlockVerificationResult> blocks;

    ChainVerificationResult(boolean parseSucceeded, List<CheckResult> chainChecks, List<BlockVerificationResult> blocks) {
        this.parseSucceeded = parseSucceeded;
        this.chainChecks = List.copyOf(chainChecks);
        this.blocks = List.copyOf(blocks);
    }

    /** 解析阶段即失败（.dat 结构性错误），无区块可逐块验证。 */
    public static ChainVerificationResult parseFailure(String detail) {
        return new ChainVerificationResult(
                false,
                List.of(CheckResult.failed("区块文件解析", CheckCategory.STRUCTURAL, detail)),
                List.of());
    }

    /**
     * 链为空且未显式允许：解析本身成功（无结构性错误），但按 fail-closed 策略判为不通过。
     * 供离线核验 CLI 默认拒绝「指错目录/空目录被当成空链通过」的审计脚枪；HTTP 自检端点对新节点空链仍判通过。
     */
    public static ChainVerificationResult emptyChainRejected(String detail) {
        return new ChainVerificationResult(
                true,
                List.of(CheckResult.failed("空链校验", CheckCategory.STRUCTURAL, detail)),
                List.of());
    }

    public boolean ok() {
        return parseSucceeded
                && chainChecks.stream().noneMatch(CheckResult::isFailure)
                && blocks.stream().allMatch(BlockVerificationResult::ok);
    }

    public boolean parseSucceeded() {
        return parseSucceeded;
    }

    public List<CheckResult> chainChecks() {
        return chainChecks;
    }

    public List<BlockVerificationResult> blocks() {
        return blocks;
    }

    public int totalBlocks() {
        return blocks.size();
    }

    public long totalMessages() {
        return blocks.stream().mapToLong(BlockVerificationResult::messageCount).sum();
    }

    public long passedCount() {
        return countStatus(CheckResult.Status.PASSED);
    }

    public long failedCount() {
        return countStatus(CheckResult.Status.FAILED);
    }

    public long skippedCount() {
        return countStatus(CheckResult.Status.SKIPPED);
    }

    /** 所有失败项（链级 + 区块级），用于快速定位问题。 */
    public List<CheckResult> allFailures() {
        List<CheckResult> failures = new ArrayList<>();
        chainChecks.stream().filter(CheckResult::isFailure).forEach(failures::add);
        for (BlockVerificationResult block : blocks) {
            failures.addAll(block.failures());
        }
        return failures;
    }

    private long countStatus(CheckResult.Status status) {
        long chainLevel = chainChecks.stream().filter(check -> check.status() == status).count();
        long blockLevel = blocks.stream()
                .flatMap(block -> block.checks().stream())
                .filter(check -> check.status() == status)
                .count();
        return chainLevel + blockLevel;
    }

    /** 人类可读的多行报告（供 CLI 打印）。 */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== NMSCI 链验证报告 =====\n");
        sb.append("结论: ").append(ok() ? "通过 (VALID)" : "不通过 (INVALID)").append('\n');
        sb.append(String.format("区块数: %d  消息数: %d  通过: %d  失败: %d  跳过: %d%n",
                totalBlocks(), totalMessages(), passedCount(), failedCount(), skippedCount()));

        sb.append("\n-- 链级验证 --\n");
        for (CheckResult check : chainChecks) {
            sb.append(formatCheck(check));
        }

        for (BlockVerificationResult block : blocks) {
            sb.append(String.format("%n-- 区块 #%d [%s]%s --%n",
                    block.height(),
                    abbreviate(block.blockIdHex()),
                    block.datFileName() == null ? "" : " (" + block.datFileName() + ")"));
            for (CheckResult check : block.checks()) {
                sb.append(formatCheck(check));
            }
        }

        if (!ok()) {
            sb.append("\n-- 失败汇总 --\n");
            for (CheckResult failure : allFailures()) {
                sb.append(formatCheck(failure));
            }
        }
        return sb.toString();
    }

    private static String formatCheck(CheckResult check) {
        String mark = switch (check.status()) {
            case PASSED -> "[PASS]";
            case FAILED -> "[FAIL]";
            case SKIPPED -> "[skip]";
        };
        return String.format("  %s %s — %s%n", mark, check.name(), check.detail());
    }

    private static String abbreviate(String hex) {
        if (hex == null || hex.length() <= 16) {
            return hex;
        }
        return hex.substring(0, 8) + "…" + hex.substring(hex.length() - 8);
    }
}
