package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.protocol.BlockDifficultyService;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

class TransactionMountMsgServiceTest {

    private static final int CURRENT_TRANSACTION_DIFFICULTY = 0x1f00ffff;
    private static final String CONSUME_LOW_S_MESSAGE = "消费节点签名不符合低S值要求";

    @Test
    void lowSFailureHappensBeforeTransactionalWriteAndAllocation() {
        TransactionMountMsgService service = new TransactionMountMsgService();
        TransactionMountMsgRepository transactionMountMsgRepository = mock(TransactionMountMsgRepository.class);
        BlockDifficultyService blockDifficultyService = mock(BlockDifficultyService.class);
        FlowNodeStateValidator flowNodeStateValidator = mock(FlowNodeStateValidator.class);
        CentralPubkeyValidator centralPubkeyValidator = mock(CentralPubkeyValidator.class);
        SignatureValidator signatureValidator = mock(SignatureValidator.class);
        TransactionMountWriteService writeService = mock(TransactionMountWriteService.class);
        TransactionMountMsg transactionMountMsg = transactionMountMsg();

        ReflectionTestUtils.setField(service, "transactionMountMsgRepository", transactionMountMsgRepository);
        ReflectionTestUtils.setField(service, "blockDifficultyService", blockDifficultyService);
        ReflectionTestUtils.setField(service, "messageWritePipeline", new MessageWritePipeline(mock(MsgAbstractService.class)));
        ReflectionTestUtils.setField(service, "flowNodeStateValidator", flowNodeStateValidator);
        ReflectionTestUtils.setField(service, "centralPubkeyValidator", centralPubkeyValidator);
        ReflectionTestUtils.setField(service, "signatureValidator", signatureValidator);
        ReflectionTestUtils.setField(service, "transactionMountWriteService", writeService);

        when(transactionMountMsgRepository.existsById(transactionMountMsg.getId())).thenReturn(false);
        when(blockDifficultyService.currentTransactionDifficultyTarget()).thenReturn(CURRENT_TRANSACTION_DIFFICULTY);
        doThrow(new IllegalArgumentException(CONSUME_LOW_S_MESSAGE))
                .when(signatureValidator)
                .validateLowS(transactionMountMsg.getConsumeNodeSignature(), CONSUME_LOW_S_MESSAGE);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveTransactionMountMsg(transactionMountMsg)
        );

        assertEquals(CONSUME_LOW_S_MESSAGE, exception.getMessage());
        verify(writeService, never()).saveAndAllocate(any());
    }

    private static TransactionMountMsg transactionMountMsg() {
        TransactionMountMsg transactionMountMsg = new TransactionMountMsg();
        transactionMountMsg.setId(UUID.randomUUID());
        transactionMountMsg.setMsgType(MsgTypeEnum.TransactionMountMsg.getValue());
        transactionMountMsg.setMountedTransactionRecordId(UUID.randomUUID());
        transactionMountMsg.setTransactionDifficultyTarget(CURRENT_TRANSACTION_DIFFICULTY);
        transactionMountMsg.setNonce(7);
        transactionMountMsg.setConsumeNodePubkey(bytes(COMPRESSED_PUBLIC_KEY_BYTES, 2));
        transactionMountMsg.setFlowNodePubkey(bytes(COMPRESSED_PUBLIC_KEY_BYTES, 40));
        transactionMountMsg.setCentralPubkey(bytes(COMPRESSED_PUBLIC_KEY_BYTES, 80));
        transactionMountMsg.setConsumeNodeSignature(bytes(RS_SIGNATURE_BYTES, 120));
        transactionMountMsg.setFlowNodeSignature(bytes(RS_SIGNATURE_BYTES, 184));
        return transactionMountMsg;
    }

    private static byte[] bytes(int size, int seed) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (seed + i);
        }
        return bytes;
    }
}
