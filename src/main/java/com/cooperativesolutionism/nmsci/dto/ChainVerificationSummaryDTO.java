package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.verifier.BlockVerificationResult;
import com.cooperativesolutionism.nmsci.verifier.ChainVerificationResult;
import com.cooperativesolutionism.nmsci.verifier.CheckResult;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code GET /verify/chain} 的链验证结果摘要：仅暴露布尔结论、计数与（受限的）失败明细，避免在大链上回传逐块全部验证项。
 */
public class ChainVerificationSummaryDTO {

    /** 失败明细最多回传条数，避免大链异常时响应体过大。 */
    private static final int MAX_FAILURES = 100;

    private boolean valid;
    private String datDirectory;
    private int blockCount;
    private long messageCount;
    private long passedChecks;
    private long failedChecks;
    private long skippedChecks;
    private boolean statefulReplayIncluded;
    private int failureCount;
    private List<FailureItem> failures;
    private String configuredCentralPubkeyHex;
    private String runningSourceCodeZipHash;

    public static ChainVerificationSummaryDTO from(
            ChainVerificationResult result,
            String datDirectory,
            boolean statefulReplayIncluded,
            String configuredCentralPubkeyHex,
            String runningSourceCodeZipHash
    ) {
        ChainVerificationSummaryDTO dto = new ChainVerificationSummaryDTO();
        dto.valid = result.ok();
        dto.datDirectory = datDirectory;
        dto.blockCount = result.totalBlocks();
        dto.messageCount = result.totalMessages();
        dto.passedChecks = result.passedCount();
        dto.failedChecks = result.failedCount();
        dto.skippedChecks = result.skippedCount();
        dto.statefulReplayIncluded = statefulReplayIncluded;
        dto.configuredCentralPubkeyHex = configuredCentralPubkeyHex;
        dto.runningSourceCodeZipHash = runningSourceCodeZipHash;

        List<FailureItem> failures = new ArrayList<>();
        for (CheckResult check : result.chainChecks()) {
            if (check.isFailure()) {
                failures.add(new FailureItem("链级", check.name(), check.category().name(), check.detail()));
            }
        }
        for (BlockVerificationResult block : result.blocks()) {
            for (CheckResult check : block.failures()) {
                failures.add(new FailureItem("区块#" + block.height(), check.name(), check.category().name(), check.detail()));
            }
        }
        dto.failureCount = failures.size();
        dto.failures = failures.size() > MAX_FAILURES
                ? new ArrayList<>(failures.subList(0, MAX_FAILURES))
                : failures;
        return dto;
    }

    public boolean isValid() {
        return valid;
    }

    public String getDatDirectory() {
        return datDirectory;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public long getMessageCount() {
        return messageCount;
    }

    public long getPassedChecks() {
        return passedChecks;
    }

    public long getFailedChecks() {
        return failedChecks;
    }

    public long getSkippedChecks() {
        return skippedChecks;
    }

    public boolean isStatefulReplayIncluded() {
        return statefulReplayIncluded;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public List<FailureItem> getFailures() {
        return failures;
    }

    public String getConfiguredCentralPubkeyHex() {
        return configuredCentralPubkeyHex;
    }

    public String getRunningSourceCodeZipHash() {
        return runningSourceCodeZipHash;
    }

    public static class FailureItem {
        private final String scope;
        private final String name;
        private final String category;
        private final String detail;

        public FailureItem(String scope, String name, String category, String detail) {
            this.scope = scope;
            this.name = name;
            this.category = category;
            this.detail = detail;
        }

        public String getScope() {
            return scope;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public String getDetail() {
            return detail;
        }
    }
}
