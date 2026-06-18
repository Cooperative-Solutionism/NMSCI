package com.cooperativesolutionism.nmsci.monitoring;

import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务自定义指标集中注册与记录点。
 *
 * <p>仅覆盖内置指标（{@code http.server.requests}、JVM、HikariCP 等）未涵盖的链业务热点：
 * 出块耗时/失败/区块大小与消息数、最新区块高度、待入块消息积压、消费链分配耗时。
 * 经 {@code /actuator/prometheus} 暴露。难度查询耗时另由 {@code BlockDifficultyService} 注册。
 */
@Component
public class NmsciMetrics {

    private final Timer blockGenerationTimer;
    private final Counter blockGenerationErrorCounter;
    private final DistributionSummary blockSizeBytes;
    private final DistributionSummary blockMessageCount;
    private final Timer consumeChainAllocationTimer;
    private final Counter blockReconcileHealedCounter;
    private final Counter blockReconcileAnomalyCounter;
    private final AtomicLong latestBlockHeight = new AtomicLong(-1L);

    public NmsciMetrics(MeterRegistry meterRegistry, MsgAbstractService msgAbstractService) {
        this.blockGenerationTimer = Timer.builder("nmsci.block.generation")
                .description("区块生成（每次定时周期）耗时")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.blockGenerationErrorCounter = Counter.builder("nmsci.block.generation.errors")
                .description("区块生成失败次数")
                .register(meterRegistry);
        this.blockSizeBytes = DistributionSummary.builder("nmsci.block.size.bytes")
                .baseUnit("bytes")
                .description("每个区块的原始字节数")
                .register(meterRegistry);
        this.blockMessageCount = DistributionSummary.builder("nmsci.block.messages")
                .baseUnit("messages")
                .description("每个区块包含的消息条数")
                .register(meterRegistry);
        this.consumeChainAllocationTimer = Timer.builder("nmsci.consumechain.allocation")
                .description("消费链分配（交易挂载）耗时")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.blockReconcileHealedCounter = Counter.builder("nmsci.block.reconcile.healed")
                .description("启动对账自动截除/删除的 .dat 文件次数（崩溃残留孤儿/撕裂块自愈）")
                .register(meterRegistry);
        this.blockReconcileAnomalyCounter = Counter.builder("nmsci.block.reconcile.anomalies")
                .description("启动对账检测到但无法安全自愈的 .dat 与 DB 偏离次数（需人工核查）")
                .register(meterRegistry);

        Gauge.builder("nmsci.block.height", latestBlockHeight, AtomicLong::doubleValue)
                .description("最新区块高度（出块侧观测；尚未出块为 -1）")
                .register(meterRegistry);
        Gauge.builder("nmsci.mempool.pending", msgAbstractService, MsgAbstractService::countPending)
                .description("待入块（未装块）消息数")
                .register(meterRegistry);
    }

    /** 计时一次出块周期。 */
    public void timeBlockGeneration(Runnable generation) {
        blockGenerationTimer.record(generation);
    }

    /** 记录一次出块失败。 */
    public void recordBlockGenerationError() {
        blockGenerationErrorCounter.increment();
    }

    /** 记录一个已生成区块的大小、消息数，并更新最新高度。 */
    public void recordGeneratedBlock(long sizeBytes, long messageCount, long height) {
        blockSizeBytes.record(sizeBytes);
        blockMessageCount.record(messageCount);
        latestBlockHeight.set(height);
    }

    /** 计时一次消费链分配。 */
    public void timeConsumeChainAllocation(Runnable allocation) {
        consumeChainAllocationTimer.record(allocation);
    }

    /** 记录一次启动对账的自愈动作（截除尾部孤儿/删除整份孤儿文件）。 */
    public void recordBlockReconcileHealed() {
        blockReconcileHealedCounter.increment();
    }

    /** 记录一次启动对账检测到的、无法安全自愈的偏离（需人工核查）。 */
    public void recordBlockReconcileAnomaly() {
        blockReconcileAnomalyCounter.increment();
    }
}
