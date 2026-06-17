package com.cooperativesolutionism.nmsci.monitoring;

import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NmsciMetricsTest {

    @Test
    void registersGaugesAndRecordsBusinessMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
        when(msgAbstractService.countPending()).thenReturn(7L);

        NmsciMetrics metrics = new NmsciMetrics(registry, msgAbstractService);

        // 启动即注册的网关指标
        assertEquals(7.0, registry.get("nmsci.mempool.pending").gauge().value());
        assertEquals(-1.0, registry.get("nmsci.block.height").gauge().value());

        metrics.timeBlockGeneration(() -> { /* no-op */ });
        assertEquals(1L, registry.get("nmsci.block.generation").timer().count());

        metrics.recordBlockGenerationError();
        assertEquals(1.0, registry.get("nmsci.block.generation.errors").counter().count());

        metrics.recordGeneratedBlock(1234L, 5L, 42L);
        assertEquals(1L, registry.get("nmsci.block.size.bytes").summary().count());
        assertEquals(1234.0, registry.get("nmsci.block.size.bytes").summary().totalAmount());
        assertEquals(5.0, registry.get("nmsci.block.messages").summary().totalAmount());
        assertEquals(42.0, registry.get("nmsci.block.height").gauge().value());

        metrics.timeConsumeChainAllocation(() -> { /* no-op */ });
        assertEquals(1L, registry.get("nmsci.consumechain.allocation").timer().count());

        // 网关随底层值实时变化
        when(msgAbstractService.countPending()).thenReturn(3L);
        assertEquals(3.0, registry.get("nmsci.mempool.pending").gauge().value());
    }
}
