package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockDifficultyServiceTest {

    @Test
    void recordsTransactionDifficultyLookupMetricWithoutCaching() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        BlockInfo latestBlock = new BlockInfo();
        latestBlock.setTransactionDifficultyTarget(0x20ffffff);
        when(blockInfoRepository.findTopByOrderByHeightDesc()).thenReturn(latestBlock);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BlockDifficultyService service = new BlockDifficultyService(blockInfoRepository, meterRegistry);

        assertEquals(0x20ffffff, service.currentTransactionDifficultyTarget());
        assertEquals(0x20ffffff, service.currentTransactionDifficultyTarget());

        verify(blockInfoRepository, times(2)).findTopByOrderByHeightDesc();
        Timer timer = meterRegistry.find("nmsci.block.difficulty.lookup")
                .tag("type", "transaction")
                .timer();
        assertNotNull(timer);
        assertEquals(2L, timer.count());
    }

    @Test
    void rejectsTransactionDifficultyLookupBeforeGenesisBlockExists() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        when(blockInfoRepository.findTopByOrderByHeightDesc()).thenReturn(null);
        BlockDifficultyService service = new BlockDifficultyService(blockInfoRepository, new SimpleMeterRegistry());

        ConflictException exception = assertThrows(ConflictException.class, service::currentTransactionDifficultyTarget);

        assertEquals("区块链尚未初始化，无法读取交易难度目标", exception.getMessage());
    }
}
