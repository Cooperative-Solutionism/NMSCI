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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockChainServiceLoopTest {

    @Test
    void generateUntilNoMessagesStopsOnEmptySelectionWithoutCounting() {
        BlockChainService service = new BlockChainService();
        MsgAbstractRepository msgAbstractRepository = mock(MsgAbstractRepository.class);
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockAssembler assembler = mock(BlockAssembler.class);
        ReflectionTestUtils.setField(service, "blockInfoRepository", mock(BlockInfoRepository.class));
        ReflectionTestUtils.setField(service, "msgAbstractRepository", msgAbstractRepository);
        ReflectionTestUtils.setField(service, "blockMessageSelector", selector);
        ReflectionTestUtils.setField(service, "blockAssembler", assembler);
        ReflectionTestUtils.setField(service, "blockFileStore", mock(BlockFileStore.class));
        ReflectionTestUtils.setField(service, "sourceCodeArchiveStore", mock(SourceCodeArchiveStore.class));
        ReflectionTestUtils.setField(service, "blockGenerationLock", mock(BlockGenerationLock.class));
        when(selector.select()).thenReturn(new SelectedBlockMessages(new LinkedHashMap<>(), 0L));

        service.generateBlockUntilNoNotInBlockMsgs();

        verify(msgAbstractRepository, never()).countByIsInBlockFalseOrderByConfirmTimestampAsc();
        verify(assembler, never()).assemble(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateBlockLocksBeforeSelectingPreviousState() {
        BlockChainService service = new BlockChainService();
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
        ReflectionTestUtils.setField(service, "blockInfoRepository", blockInfoRepository);
        ReflectionTestUtils.setField(service, "msgAbstractRepository", msgAbstractRepository);
        ReflectionTestUtils.setField(service, "blockMessageSelector", selector);
        ReflectionTestUtils.setField(service, "blockAssembler", assembler);
        ReflectionTestUtils.setField(service, "blockFileStore", blockFileStore);
        ReflectionTestUtils.setField(service, "sourceCodeArchiveStore", sourceCodeArchiveStore);
        ReflectionTestUtils.setField(service, "blockGenerationLock", blockGenerationLock);
        ReflectionTestUtils.setField(service, "nmsciMetrics", new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));
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
        BlockChainService service = new BlockChainService();
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
        ReflectionTestUtils.setField(service, "blockInfoRepository", blockInfoRepository);
        ReflectionTestUtils.setField(service, "msgAbstractRepository", msgAbstractRepository);
        ReflectionTestUtils.setField(service, "blockMessageSelector", selector);
        ReflectionTestUtils.setField(service, "blockAssembler", assembler);
        ReflectionTestUtils.setField(service, "blockFileStore", blockFileStore);
        ReflectionTestUtils.setField(service, "sourceCodeArchiveStore", sourceCodeArchiveStore);
        ReflectionTestUtils.setField(service, "blockGenerationLock", blockGenerationLock);
        ReflectionTestUtils.setField(service, "nmsciMetrics", new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class)));
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
        BlockChainService service = new BlockChainService();
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockGenerationLock blockGenerationLock = mock(BlockGenerationLock.class);
        ReflectionTestUtils.setField(service, "blockInfoRepository", mock(BlockInfoRepository.class));
        ReflectionTestUtils.setField(service, "msgAbstractRepository", mock(MsgAbstractRepository.class));
        ReflectionTestUtils.setField(service, "blockMessageSelector", selector);
        ReflectionTestUtils.setField(service, "blockAssembler", mock(BlockAssembler.class));
        ReflectionTestUtils.setField(service, "blockFileStore", mock(BlockFileStore.class));
        ReflectionTestUtils.setField(service, "sourceCodeArchiveStore", mock(SourceCodeArchiveStore.class));
        ReflectionTestUtils.setField(service, "blockGenerationLock", blockGenerationLock);
        when(selector.select()).thenReturn(new SelectedBlockMessages(new LinkedHashMap<>(), 0L));

        service.generateBlockUntilNoNotInBlockMsgs();

        InOrder inOrder = inOrder(blockGenerationLock, selector);
        inOrder.verify(blockGenerationLock).lock();
        inOrder.verify(selector).select();
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
