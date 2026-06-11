package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.service.impl.BlockChainServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;

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
        when(selector.select()).thenReturn(new SelectedBlockMessages(new LinkedHashMap<>(), 0L));

        service.generateBlockUntilNoNotInBlockMsgs();

        verify(msgAbstractRepository, never()).countByIsInBlockFalseOrderByConfirmTimestampAsc();
        verify(assembler, never()).assemble(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
