package com.cooperativesolutionism.nmsci.task;

import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Provider;
import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GenerateBlockTaskTest {

    @Test
    void logsAndSuppressesBlockGenerationFailureForScheduler() {
        BlockChainService blockChainService = mock(BlockChainService.class);
        doThrow(new IllegalStateException("db unavailable")).when(blockChainService).generateBlock();

        GenerateBlockTask task = new GenerateBlockTask(
                blockChainService,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));

        assertDoesNotThrow(task::execute);

        verify(blockChainService).generateBlock();
        verify(blockChainService, never()).generateBlockUntilNoNotInBlockMsgs();
    }

    @Test
    void logsAndSuppressesFailureWhenFailureDurationCannotBeCalculated() {
        BlockChainService blockChainService = mock(BlockChainService.class);
        doThrow(new IllegalStateException("db unavailable")).when(blockChainService).generateBlock();

        GenerateBlockTask task = new GenerateBlockTask(
                blockChainService,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));

        try (MockedStatic<DateUtil> dateUtil = mockStatic(DateUtil.class)) {
            dateUtil.when(DateUtil::getCurrentMicros)
                    .thenReturn(1781530000000000L)
                    .thenThrow(new IllegalStateException("clock unavailable"));

            assertDoesNotThrow(task::execute);

            dateUtil.verify(DateUtil::getCurrentMicros, times(2));
        }
        verify(blockChainService).generateBlock();
        verify(blockChainService, never()).generateBlockUntilNoNotInBlockMsgs();
    }

    @Test
    void logsAndSuppressesProviderSetupFailureForScheduler() {
        BlockChainService blockChainService = mock(BlockChainService.class);
        GenerateBlockTask task = new GenerateBlockTask(
                blockChainService,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));

        try (MockedStatic<Security> security = mockStatic(Security.class)) {
            security.when(() -> Security.addProvider(any(Provider.class)))
                    .thenThrow(new SecurityException("provider unavailable"));

            assertDoesNotThrow(task::execute);

            security.verify(() -> Security.addProvider(any(Provider.class)));
        }
        verify(blockChainService, never()).generateBlock();
        verify(blockChainService, never()).generateBlockUntilNoNotInBlockMsgs();
    }

    @Test
    void logsAndSuppressesTimestampFailureForScheduler() {
        BlockChainService blockChainService = mock(BlockChainService.class);
        GenerateBlockTask task = new GenerateBlockTask(
                blockChainService,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));

        try (MockedStatic<DateUtil> dateUtil = mockStatic(DateUtil.class)) {
            dateUtil.when(DateUtil::getCurrentMicros)
                    .thenThrow(new IllegalStateException("clock unavailable"));

            assertDoesNotThrow(task::execute);

            dateUtil.verify(DateUtil::getCurrentMicros);
        }
        verify(blockChainService, never()).generateBlock();
        verify(blockChainService, never()).generateBlockUntilNoNotInBlockMsgs();
    }

    @Test
    void retriesFirstRunCatchUpWhenPreviousCatchUpFailed() {
        BlockChainService blockChainService = mock(BlockChainService.class);
        doThrow(new IllegalStateException("catch-up failed"))
                .doNothing()
                .when(blockChainService)
                .generateBlockUntilNoNotInBlockMsgs();

        GenerateBlockTask task = new GenerateBlockTask(
                blockChainService,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));

        assertDoesNotThrow(task::execute);
        assertDoesNotThrow(task::execute);

        verify(blockChainService, times(2)).generateBlock();
        verify(blockChainService, times(2)).generateBlockUntilNoNotInBlockMsgs();
    }
}
