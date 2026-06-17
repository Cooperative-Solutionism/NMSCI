package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowNodeRegisterMsgServiceTest {

    @Test
    void rejectsRegistrationBeforeGenesisBlockExists() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        when(blockInfoRepository.findTopByOrderByHeightDesc()).thenReturn(null);

        FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository = mock(FlowNodeRegisterMsgRepository.class);
        FlowNodeRegisterMsgService service = new FlowNodeRegisterMsgService();
        ReflectionTestUtils.setField(service, "blockInfoRepository", blockInfoRepository);
        ReflectionTestUtils.setField(service, "flowNodeRegisterMsgRepository", flowNodeRegisterMsgRepository);
        ReflectionTestUtils.setField(service, "messageWritePipeline", new MessageWritePipeline(mock(MsgAbstractService.class)));

        FlowNodeRegisterMsg msg = new FlowNodeRegisterMsg();
        msg.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msg.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        ConflictException exception = assertThrows(ConflictException.class, () -> service.saveFlowNodeRegisterMsg(msg));

        assertEquals("区块链尚未初始化，无法注册流转节点", exception.getMessage());
    }
}
