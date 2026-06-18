package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockChainServiceLoopTest {

    @Test
    void generateUntilNoMessagesStopsOnEmptySelectionWithoutCounting() {
        MsgAbstractRepository msgAbstractRepository = mock(MsgAbstractRepository.class);
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockAssembler assembler = mock(BlockAssembler.class);
        BlockChainService service = new BlockChainService(
                mock(BlockInfoRepository.class),
                msgAbstractRepository,
                selector,
                assembler,
                mock(BlockFileStore.class),
                mock(SourceCodeArchiveStore.class),
                mock(BlockGenerationLock.class),
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)),
                mock(BlockFileReconciler.class));
        when(selector.select()).thenReturn(new SelectedBlockMessages(new LinkedHashMap<>(), 0L));

        service.generateBlockUntilNoNotInBlockMsgs();

        verify(msgAbstractRepository, never()).countByIsInBlockFalseOrderByConfirmTimestampAsc();
        verify(assembler, never()).assemble(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateBlockLocksBeforeSelectingPreviousState() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        MsgAbstractRepository msgAbstractRepository = mock(MsgAbstractRepository.class);
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockAssembler assembler = mock(BlockAssembler.class);
        BlockFileStore blockFileStore = mock(BlockFileStore.class);
        SourceCodeArchiveStore sourceCodeArchiveStore = mock(SourceCodeArchiveStore.class);
        BlockGenerationLock blockGenerationLock = mock(BlockGenerationLock.class);
        SelectedBlockMessages selectedMessages = new SelectedBlockMessages(new LinkedHashMap<>(), 0L);
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setHeight(0L);
        blockInfo.setRawBytes(new byte[]{1, 2, 3});
        AssembledBlock assembledBlock = new AssembledBlock(blockInfo, new byte[]{1, 2, 3}, List.of());
        BlockChainService service = new BlockChainService(
                blockInfoRepository,
                msgAbstractRepository,
                selector,
                assembler,
                blockFileStore,
                sourceCodeArchiveStore,
                blockGenerationLock,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)),
                mock(BlockFileReconciler.class));
        when(selector.select()).thenReturn(selectedMessages);
        when(assembler.assemble(null, selectedMessages)).thenReturn(assembledBlock);
        when(blockFileStore.appendBlock(null, assembledBlock.getDatBytes())).thenReturn("blk00000000.dat");
        when(sourceCodeArchiveStore.copyArchiveForVersion(blockInfo.getVersion())).thenReturn("source_code_v0.zip");

        service.generateBlock();

        InOrder inOrder = inOrder(blockGenerationLock, selector);
        inOrder.verify(blockGenerationLock).lock();
        inOrder.verify(selector).select();
        verify(blockInfoRepository).save(blockInfo);
    }

    @Test
    void generateBlockMarksSelectedMessagesBeforeSavingThem() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        MsgAbstractRepository msgAbstractRepository = mock(MsgAbstractRepository.class);
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockAssembler assembler = mock(BlockAssembler.class);
        BlockFileStore blockFileStore = mock(BlockFileStore.class);
        SourceCodeArchiveStore sourceCodeArchiveStore = mock(SourceCodeArchiveStore.class);
        BlockGenerationLock blockGenerationLock = mock(BlockGenerationLock.class);
        MsgAbstract msgAbstract = msgAbstract();
        SelectedBlockMessages selectedMessages = selectedMessages(msgAbstract);
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setVersion(0);
        blockInfo.setHeight(0L);
        blockInfo.setRawBytes(new byte[]{1, 2, 3});
        AssembledBlock assembledBlock = new AssembledBlock(blockInfo, new byte[]{1, 2, 3}, List.of(msgAbstract));
        BlockChainService service = new BlockChainService(
                blockInfoRepository,
                msgAbstractRepository,
                selector,
                assembler,
                blockFileStore,
                sourceCodeArchiveStore,
                blockGenerationLock,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)),
                mock(BlockFileReconciler.class));
        when(selector.select()).thenReturn(selectedMessages);
        when(assembler.assemble(null, selectedMessages)).thenReturn(assembledBlock);
        when(blockFileStore.appendBlock(null, assembledBlock.getDatBytes())).thenReturn("blk00000000.dat");
        when(sourceCodeArchiveStore.copyArchiveForVersion(blockInfo.getVersion())).thenReturn("source_code_v0.zip");
        doAnswer(invocation -> {
            List<MsgAbstract> savedMessages = invocation.getArgument(0);
            assertTrue(savedMessages.stream().allMatch(MsgAbstract::getIsInBlock));
            return savedMessages;
        }).when(msgAbstractRepository).saveAll(List.of(msgAbstract));

        service.generateBlock();

        verify(msgAbstractRepository).saveAll(List.of(msgAbstract));
    }

    @Test
    void generateUntilLocksBeforeCheckingForMoreMessages() {
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockGenerationLock blockGenerationLock = mock(BlockGenerationLock.class);
        BlockChainService service = new BlockChainService(
                mock(BlockInfoRepository.class),
                mock(MsgAbstractRepository.class),
                selector,
                mock(BlockAssembler.class),
                mock(BlockFileStore.class),
                mock(SourceCodeArchiveStore.class),
                blockGenerationLock,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)),
                mock(BlockFileReconciler.class));
        when(selector.select()).thenReturn(new SelectedBlockMessages(new LinkedHashMap<>(), 0L));

        service.generateBlockUntilNoNotInBlockMsgs();

        InOrder inOrder = inOrder(blockGenerationLock, selector);
        inOrder.verify(blockGenerationLock).lock();
        inOrder.verify(selector).select();
    }

    @Test
    void reconcilesOnceBeforeAppendAcrossBothGenerationPaths() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockAssembler assembler = mock(BlockAssembler.class);
        BlockFileStore blockFileStore = mock(BlockFileStore.class);
        SourceCodeArchiveStore sourceCodeArchiveStore = mock(SourceCodeArchiveStore.class);
        BlockGenerationLock blockGenerationLock = mock(BlockGenerationLock.class);
        BlockFileReconciler reconciler = mock(BlockFileReconciler.class);
        SelectedBlockMessages emptySelection = new SelectedBlockMessages(new LinkedHashMap<>(), 0L);
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setHeight(0L);
        blockInfo.setRawBytes(new byte[]{1, 2, 3});
        AssembledBlock assembledBlock = new AssembledBlock(blockInfo, new byte[]{1, 2, 3}, List.of());
        BlockChainService service = new BlockChainService(
                blockInfoRepository,
                mock(MsgAbstractRepository.class),
                selector,
                assembler,
                blockFileStore,
                sourceCodeArchiveStore,
                blockGenerationLock,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)),
                reconciler);
        when(selector.select()).thenReturn(emptySelection);
        when(assembler.assemble(null, emptySelection)).thenReturn(assembledBlock);
        when(blockFileStore.appendBlock(null, assembledBlock.getDatBytes())).thenReturn("blk00000000.dat");
        when(sourceCodeArchiveStore.copyArchiveForVersion(blockInfo.getVersion())).thenReturn("source_code_v0.zip");

        // 中心公钥冻结的同步出块路径先触发（评审指出它绕过了原对账门控），再触发定时任务路径：
        // 对账全程只跑一次，且在持锁之后、任何 appendBlock 之前。
        service.generateBlockUntilNoNotInBlockMsgs();
        service.generateBlock();

        verify(reconciler, times(1)).reconcileOnStartup();
        InOrder inOrder = inOrder(blockGenerationLock, reconciler, blockFileStore);
        inOrder.verify(blockGenerationLock).lock();
        inOrder.verify(reconciler).reconcileOnStartup();
        inOrder.verify(blockFileStore).appendBlock(null, assembledBlock.getDatBytes());
    }

    private static SelectedBlockMessages selectedMessages(MsgAbstract msgAbstract) {
        Map<MsgTypeEnum, List<MsgAbstract>> messagesByType = new LinkedHashMap<>();
        messagesByType.put(MsgTypeEnum.FlowNodeRegisterMsg, List.of(msgAbstract));
        return new SelectedBlockMessages(messagesByType, 1L);
    }

    private static MsgAbstract msgAbstract() {
        MsgAbstract msgAbstract = new MsgAbstract();
        msgAbstract.setId(new byte[]{1});
        msgAbstract.setMsgId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        msgAbstract.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msgAbstract.setConfirmTimestamp(1L);
        msgAbstract.setIsInBlock(false);
        return msgAbstract;
    }
}
