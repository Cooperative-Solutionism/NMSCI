package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockMessageSelectorTest {

    @Test
    void selectsMessagesWithKeysetBatches() {
        MsgAbstractRepository msgAbstractRepository = mock(MsgAbstractRepository.class);
        BlockMessageSelector selector = new BlockMessageSelector(properties(), msgAbstractRepository);

        MsgAbstract first = msgAbstract(1, 10L);
        MsgAbstract second = msgAbstract(2, 20L);
        when(msgAbstractRepository.findNextNotInBlockBatch(null, null, 1000))
                .thenReturn(List.of(first, second));
        when(msgAbstractRepository.findNextNotInBlockBatch(20L, second.getId(), 1000))
                .thenReturn(List.of());

        SelectedBlockMessages selected = selector.select();

        assertEquals(2, selected.getAllMessages().size());
        assertEquals(20L, selected.getMaxMsgTimestamp());
        verify(msgAbstractRepository).findNextNotInBlockBatch(null, null, 1000);
        verify(msgAbstractRepository).findNextNotInBlockBatch(20L, second.getId(), 1000);
    }

    private static NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        properties.setBlockHeaderSize(229);
        properties.setBlockMaxSize(10_000L);
        return properties;
    }

    private static MsgAbstract msgAbstract(int idSeed, long confirmTimestamp) {
        MsgAbstract msgAbstract = new MsgAbstract();
        msgAbstract.setId(new byte[]{(byte) idSeed});
        msgAbstract.setMsgId(UUID.randomUUID());
        msgAbstract.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msgAbstract.setConfirmTimestamp(confirmTimestamp);
        msgAbstract.setIsInBlock(false);
        return msgAbstract;
    }
}
