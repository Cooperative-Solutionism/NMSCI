package com.cooperativesolutionism.nmsci.verifier;

import java.util.List;

/**
 * 单个区块的验证结果。
 *
 * @param height       区块高度
 * @param blockIdHex   区块 id 的十六进制
 * @param datFileName  来源 .dat 文件名（可能为 null）
 * @param messageCount 区块体内消息条数
 * @param checks       该区块的全部验证项结果
 */
public record BlockVerificationResult(
        long height,
        String blockIdHex,
        String datFileName,
        int messageCount,
        List<CheckResult> checks
) {

    public BlockVerificationResult {
        checks = List.copyOf(checks);
    }

    public boolean ok() {
        return checks.stream().noneMatch(CheckResult::isFailure);
    }

    public List<CheckResult> failures() {
        return checks.stream().filter(CheckResult::isFailure).toList();
    }
}
