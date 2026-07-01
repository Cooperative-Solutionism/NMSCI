package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.model.BlockInfoSummary;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 运营快照：最新区块、待入块消息池、出块周期与中心公钥冻结状态。
 */
public class SystemStatusDTO {

    private Long latestBlockHeight;

    private String latestBlockHash;

    private Long latestBlockTimestamp;

    private long pendingMessageCount;

    private Long oldestPendingConfirmTimestamp;

    private long blockIntervalMs;

    /**
     * 当前中心公钥是否已被冻结。运行期通常为 false——中心公钥冻结确认后会补齐待入块消息并请求优雅停机，
     * 故该字段更多用于历史/契约完整性。
     */
    private boolean currentCentralPubkeyLocked;

    public static SystemStatusDTO from(
            BlockInfoSummary latestBlock,
            long pendingMessageCount,
            Long oldestPendingConfirmTimestamp,
            long blockIntervalMs,
            boolean currentCentralPubkeyLocked
    ) {
        SystemStatusDTO dto = new SystemStatusDTO();
        if (latestBlock != null) {
            dto.setLatestBlockHeight(latestBlock.getHeight());
            dto.setLatestBlockHash(ByteArrayUtil.bytesToHex(latestBlock.getId()));
            dto.setLatestBlockTimestamp(latestBlock.getTimestamp());
        }
        dto.setPendingMessageCount(pendingMessageCount);
        dto.setOldestPendingConfirmTimestamp(oldestPendingConfirmTimestamp);
        dto.setBlockIntervalMs(blockIntervalMs);
        dto.setCurrentCentralPubkeyLocked(currentCentralPubkeyLocked);
        return dto;
    }

    public Long getLatestBlockHeight() {
        return latestBlockHeight;
    }

    public void setLatestBlockHeight(Long latestBlockHeight) {
        this.latestBlockHeight = latestBlockHeight;
    }

    public String getLatestBlockHash() {
        return latestBlockHash;
    }

    public void setLatestBlockHash(String latestBlockHash) {
        this.latestBlockHash = latestBlockHash;
    }

    public Long getLatestBlockTimestamp() {
        return latestBlockTimestamp;
    }

    public void setLatestBlockTimestamp(Long latestBlockTimestamp) {
        this.latestBlockTimestamp = latestBlockTimestamp;
    }

    public long getPendingMessageCount() {
        return pendingMessageCount;
    }

    public void setPendingMessageCount(long pendingMessageCount) {
        this.pendingMessageCount = pendingMessageCount;
    }

    public Long getOldestPendingConfirmTimestamp() {
        return oldestPendingConfirmTimestamp;
    }

    public void setOldestPendingConfirmTimestamp(Long oldestPendingConfirmTimestamp) {
        this.oldestPendingConfirmTimestamp = oldestPendingConfirmTimestamp;
    }

    public long getBlockIntervalMs() {
        return blockIntervalMs;
    }

    public void setBlockIntervalMs(long blockIntervalMs) {
        this.blockIntervalMs = blockIntervalMs;
    }

    @JsonProperty("currentCentralPubkeyLocked")
    public boolean isCurrentCentralPubkeyLocked() {
        return currentCentralPubkeyLocked;
    }

    public void setCurrentCentralPubkeyLocked(boolean currentCentralPubkeyLocked) {
        this.currentCentralPubkeyLocked = currentCentralPubkeyLocked;
    }
}
