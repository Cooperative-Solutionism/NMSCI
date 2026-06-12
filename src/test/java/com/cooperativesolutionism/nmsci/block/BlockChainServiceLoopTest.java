package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.service.impl.BlockChainServiceImpl;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;

import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockChainServiceLoopTest {

    @Test
    void generateUntilNoMessagesStopsOnEmptySelectionWithoutCounting() {
        BlockChainServiceImpl service = new BlockChainServiceImpl();
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
        BlockChainServiceImpl service = new BlockChainServiceImpl();
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        MsgAbstractRepository msgAbstractRepository = mock(MsgAbstractRepository.class);
        BlockMessageSelector selector = mock(BlockMessageSelector.class);
        BlockAssembler assembler = mock(BlockAssembler.class);
        BlockFileStore blockFileStore = mock(BlockFileStore.class);
        SourceCodeArchiveStore sourceCodeArchiveStore = mock(SourceCodeArchiveStore.class);
        BlockGenerationLock blockGenerationLock = mock(BlockGenerationLock.class);
        SelectedBlockMessages selectedMessages = new SelectedBlockMessages(new LinkedHashMap<>(), 0L);
        BlockInfo blockInfo = new BlockInfo();
        AssembledBlock assembledBlock = new AssembledBlock(blockInfo, new byte[]{1, 2, 3}, List.of());
        ReflectionTestUtils.setField(service, "blockInfoRepository", blockInfoRepository);
        ReflectionTestUtils.setField(service, "msgAbstractRepository", msgAbstractRepository);
        ReflectionTestUtils.setField(service, "blockMessageSelector", selector);
        ReflectionTestUtils.setField(service, "blockAssembler", assembler);
        ReflectionTestUtils.setField(service, "blockFileStore", blockFileStore);
        ReflectionTestUtils.setField(service, "sourceCodeArchiveStore", sourceCodeArchiveStore);
        ReflectionTestUtils.setField(service, "blockGenerationLock", blockGenerationLock);
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
    void generateUntilLocksBeforeCheckingForMoreMessages() {
        BlockChainServiceImpl service = new BlockChainServiceImpl();
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
}
