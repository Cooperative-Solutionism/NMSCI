package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.service.MessageWritePipeline;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionRecordAmountValidationTest {

    private static final int CURRENT_TRANSACTION_DIFFICULTY = 0x20ffffff;
    private static final int STALE_TRANSACTION_DIFFICULTY = 0x20fffffe;

    private TransactionRecordMsgService service;
    private TransactionRecordMsgRepository transactionRecordMsgRepository;
    private BlockDifficultyService blockDifficultyService;

    @BeforeEach
    void setUp() {
        transactionRecordMsgRepository = mock(TransactionRecordMsgRepository.class);
        blockDifficultyService = mock(BlockDifficultyService.class);
        service = new TransactionRecordMsgService(blockDifficultyService, transactionRecordMsgRepository, new MessageWritePipeline(mock(MsgAbstractService.class)), null, null, null, null, null, null);
    }

    @Test
    void rejectsNullZeroAndNegativeTransactionAmountsBeforeOtherPersistenceValidation() {
        for (Long invalidAmount : new Long[]{null, 0L, -1L, Long.MIN_VALUE}) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.saveTransactionRecordMsg(transactionRecord(invalidAmount))
            );

            assertEquals("交易金额必须为正数", exception.getMessage());
        }

        verifyNoInteractions(transactionRecordMsgRepository, blockDifficultyService);
    }

    @Test
    void acceptsLongMaxValueAsPositiveTransactionAmountBoundary() {
        TransactionRecordMsg transactionRecordMsg = transactionRecord(Long.MAX_VALUE);
        when(transactionRecordMsgRepository.existsById(any())).thenReturn(false);
        when(blockDifficultyService.currentTransactionDifficultyTarget()).thenReturn(STALE_TRANSACTION_DIFFICULTY);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveTransactionRecordMsg(transactionRecordMsg)
        );

        assertEquals("交易难度目标与前区块中的交易难度目标不一致", exception.getMessage());
        verify(transactionRecordMsgRepository).existsById(transactionRecordMsg.getId());
        verify(blockDifficultyService).currentTransactionDifficultyTarget();
    }

    private static TransactionRecordMsg transactionRecord(Long amount) {
        TransactionRecordMsg transactionRecordMsg = new TransactionRecordMsg();
        transactionRecordMsg.setId(UUID.randomUUID());
        transactionRecordMsg.setMsgType(MsgTypeEnum.TransactionRecordMsg.getValue());
        transactionRecordMsg.setAmount(amount);
        transactionRecordMsg.setCurrencyType(CurrencyTypeEnum.CNY.getValue());
        transactionRecordMsg.setTransactionDifficultyTarget(CURRENT_TRANSACTION_DIFFICULTY);
        return transactionRecordMsg;
    }
}
