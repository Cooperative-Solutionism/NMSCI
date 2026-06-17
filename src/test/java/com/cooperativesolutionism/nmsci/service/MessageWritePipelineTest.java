package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class MessageWritePipelineTest {

    private final MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
    private final MessageWritePipeline pipeline = new MessageWritePipeline(msgAbstractService);

    @Test
    void rejectsUnexpectedMessageType() {
        FlowNodeRegisterMsg message = new FlowNodeRegisterMsg();
        message.setMsgType(MsgTypeEnum.TransactionRecordMsg.getValue());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> pipeline.requireMsgType(message, MsgTypeEnum.FlowNodeRegisterMsg)
        );

        assertEquals("信息类型错误，必须为" + MsgTypeEnum.FlowNodeRegisterMsg.getValue(), exception.getMessage());
    }

    @Test
    void rejectsExistingIdWithSuppliedConflictMessage() {
        FlowNodeRegisterMsg message = new FlowNodeRegisterMsg();
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        message.setId(id);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> pipeline.rejectExistingId(message, candidate -> candidate.equals(id), () -> "duplicate " + id)
        );

        assertEquals("duplicate " + id, exception.getMessage());
    }

    @Test
    void savesAbstractBeforeEntityWhenRequested() {
        FlowNodeRegisterMsg message = new FlowNodeRegisterMsg();
        List<String> phases = new ArrayList<>();
        doAnswer(invocation -> {
            phases.add("abstract");
            return null;
        }).when(msgAbstractService).saveMsgAbstract(message);

        FlowNodeRegisterMsg saved = pipeline.saveAbstractThenEntity(message, msg -> {
            phases.add("entity");
            return msg;
        });

        assertSame(message, saved);
        assertEquals(List.of("abstract", "entity"), phases);
    }

    @Test
    void savesEntityBeforeAbstractWhenRequested() {
        FlowNodeRegisterMsg message = new FlowNodeRegisterMsg();
        List<String> phases = new ArrayList<>();
        doAnswer(invocation -> {
            phases.add("abstract");
            return null;
        }).when(msgAbstractService).saveMsgAbstract(message);

        FlowNodeRegisterMsg saved = pipeline.saveEntityThenAbstract(message, msg -> {
            phases.add("entity");
            return msg;
        });

        assertSame(message, saved);
        assertEquals(List.of("entity", "abstract"), phases);
    }

    @Test
    void populatesRawBytesAndTxidFromVerifyDataAndSignatures() {
        FlowNodeRegisterMsg message = new FlowNodeRegisterMsg();
        byte[] expectedRawBytes = new byte[] {1, 2, 3, 4, 5};

        pipeline.populateRawBytes(message, new byte[] {1, 2}, new byte[] {3}, new byte[] {4, 5});

        assertArrayEquals(expectedRawBytes, message.getRawBytes());
        assertArrayEquals(MerkleTreeUtil.calcTxid(expectedRawBytes), message.getTxid());
    }
}
