package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

/**
 * 校验 {@link TransactionMountWriteService} 在分配事务内对流转节点/中心公钥状态做二次核验（review #4 的 TOCTOU 收口）：
 * 锁定/授权状态须在 {@code @Transactional} 的 saveAndAllocate 内重新检查，且任一校验失败必须在落库/分配之前短路。
 */
class TransactionMountWriteServiceTest {

    @Test
    void revalidatesFlowNodeAndCentralLockStateInsideAllocationTransaction() {
        Fixture fixture = new Fixture();

        when(fixture.transactionRecordMsgRepository.findByIdForUpdate(fixture.recordId))
                .thenReturn(Optional.of(fixture.transactionRecordMsg));
        when(fixture.transactionMountMsgRepository.existsTransactionMountMsgByMountedTransactionRecordId(fixture.recordId))
                .thenReturn(false);
        when(fixture.centralPubkeyValidator.currentCentralPubkey()).thenReturn(fixture.centralPubkey.clone());
        when(fixture.messageWritePipeline.saveEntityThenAbstract(eq(fixture.transactionMountMsg), any()))
                .thenReturn(fixture.transactionMountMsg);

        TransactionMountMsg result = fixture.writeService.saveAndAllocate(fixture.transactionMountMsg);

        assertSame(fixture.transactionMountMsg, result);
        // 二次校验确实在分配事务内被调用
        verify(fixture.flowNodeStateValidator)
                .validateRegisteredAuthorizedAndNotLocked(eq(fixture.flowNodePubkey), eq(fixture.centralPubkey));
        verify(fixture.centralPubkeyValidator).validateNotLocked(fixture.centralPubkey);
        // 并且最终确实落库 + 分配
        verify(fixture.consumeChainAllocationService)
                .saveConsumeChain(fixture.transactionMountMsg, fixture.transactionRecordMsg);
    }

    @Test
    void rejectsAndSkipsAllocationWhenFlowNodeBecameLockedBeforeCommit() {
        Fixture fixture = new Fixture();

        when(fixture.centralPubkeyValidator.currentCentralPubkey()).thenReturn(fixture.centralPubkey.clone());
        doThrow(new IllegalArgumentException("该流转节点公钥已冻结"))
                .when(fixture.flowNodeStateValidator)
                .validateRegisteredAuthorizedAndNotLocked(any(), any());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.writeService.saveAndAllocate(fixture.transactionMountMsg)
        );

        assertEquals("该流转节点公钥已冻结", exception.getMessage());
        // 流转节点校验在最前短路：中心公钥校验、取记录行锁、落库、分配均不应执行
        verify(fixture.centralPubkeyValidator, never()).validateNotLocked(any());
        verify(fixture.transactionRecordMsgRepository, never()).findByIdForUpdate(any());
        verify(fixture.messageWritePipeline, never()).saveEntityThenAbstract(any(), any());
        verify(fixture.consumeChainAllocationService, never()).saveConsumeChain(any(), any());
    }

    @Test
    void rejectsAndSkipsAllocationWhenCentralPubkeyBecameLockedBeforeCommit() {
        Fixture fixture = new Fixture();

        when(fixture.centralPubkeyValidator.currentCentralPubkey()).thenReturn(fixture.centralPubkey.clone());
        doThrow(new IllegalArgumentException("该中心公钥已被冻结"))
                .when(fixture.centralPubkeyValidator)
                .validateNotLocked(fixture.centralPubkey);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.writeService.saveAndAllocate(fixture.transactionMountMsg)
        );

        assertEquals("该中心公钥已被冻结", exception.getMessage());
        verify(fixture.transactionRecordMsgRepository, never()).findByIdForUpdate(any());
        verify(fixture.messageWritePipeline, never()).saveEntityThenAbstract(any(), any());
        verify(fixture.consumeChainAllocationService, never()).saveConsumeChain(any(), any());
    }

    private static final class Fixture {
        final TransactionRecordMsgRepository transactionRecordMsgRepository = mock(TransactionRecordMsgRepository.class);
        final TransactionMountMsgRepository transactionMountMsgRepository = mock(TransactionMountMsgRepository.class);
        final MessageWritePipeline messageWritePipeline = mock(MessageWritePipeline.class);
        final ConsumeChainAllocationService consumeChainAllocationService = mock(ConsumeChainAllocationService.class);
        final FlowNodeStateValidator flowNodeStateValidator = mock(FlowNodeStateValidator.class);
        final CentralPubkeyValidator centralPubkeyValidator = mock(CentralPubkeyValidator.class);

        final UUID recordId = UUID.randomUUID();
        final byte[] flowNodePubkey = bytes(40);
        final byte[] consumeNodePubkey = bytes(2);
        final byte[] centralPubkey = bytes(80);

        final TransactionMountMsg transactionMountMsg = transactionMountMsg();
        final TransactionRecordMsg transactionRecordMsg = transactionRecordMsg();

        final TransactionMountWriteService writeService = new TransactionMountWriteService(
                transactionRecordMsgRepository,
                transactionMountMsgRepository,
                messageWritePipeline,
                consumeChainAllocationService,
                flowNodeStateValidator,
                centralPubkeyValidator
        );

        private TransactionMountMsg transactionMountMsg() {
            TransactionMountMsg msg = new TransactionMountMsg();
            msg.setId(UUID.randomUUID());
            msg.setMountedTransactionRecordId(recordId);
            msg.setFlowNodePubkey(flowNodePubkey);
            msg.setConsumeNodePubkey(consumeNodePubkey);
            msg.setCentralPubkey(centralPubkey);
            return msg;
        }

        private TransactionRecordMsg transactionRecordMsg() {
            TransactionRecordMsg msg = new TransactionRecordMsg();
            msg.setId(recordId);
            msg.setConsumeNodePubkey(consumeNodePubkey);
            return msg;
        }

        private static byte[] bytes(int seed) {
            byte[] value = new byte[COMPRESSED_PUBLIC_KEY_BYTES];
            for (int i = 0; i < value.length; i++) {
                value[i] = (byte) (seed + i);
            }
            return value;
        }
    }
}
