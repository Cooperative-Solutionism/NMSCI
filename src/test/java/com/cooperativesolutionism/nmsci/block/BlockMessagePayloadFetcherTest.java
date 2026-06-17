package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.MessagePayloadProjection;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockMessagePayloadFetcherTest {

    private final FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository = mock(FlowNodeRegisterMsgRepository.class);
    private final CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository = mock(CentralPubkeyEmpowerMsgRepository.class);
    private final CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository = mock(CentralPubkeyLockedMsgRepository.class);
    private final FlowNodeLockedMsgRepository flowNodeLockedMsgRepository = mock(FlowNodeLockedMsgRepository.class);
    private final TransactionRecordMsgRepository transactionRecordMsgRepository = mock(TransactionRecordMsgRepository.class);
    private final TransactionMountMsgRepository transactionMountMsgRepository = mock(TransactionMountMsgRepository.class);

    private final BlockMessagePayloadFetcher fetcher = new BlockMessagePayloadFetcher(
            flowNodeRegisterMsgRepository,
            centralPubkeyEmpowerMsgRepository,
            centralPubkeyLockedMsgRepository,
            flowNodeLockedMsgRepository,
            transactionRecordMsgRepository,
            transactionMountMsgRepository
    );

    @Test
    void routesEachMessageTypeToItsPayloadProjectionRepository() {
        UUID registerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID centralLockedId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID flowLockedId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID recordId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID mountId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        when(flowNodeRegisterMsgRepository.findPayloadByIdIn(List.of(registerId))).thenReturn(payloads(registerId));
        when(centralPubkeyEmpowerMsgRepository.findPayloadByIdIn(List.of(empowerId))).thenReturn(payloads(empowerId));
        when(centralPubkeyLockedMsgRepository.findPayloadByIdIn(List.of(centralLockedId))).thenReturn(payloads(centralLockedId));
        when(flowNodeLockedMsgRepository.findPayloadByIdIn(List.of(flowLockedId))).thenReturn(payloads(flowLockedId));
        when(transactionRecordMsgRepository.findPayloadByIdIn(List.of(recordId))).thenReturn(payloads(recordId));
        when(transactionMountMsgRepository.findPayloadByIdIn(List.of(mountId))).thenReturn(payloads(mountId));

        assertEquals(registerId, fetcher.findPayloads(MsgTypeEnum.FlowNodeRegisterMsg, List.of(registerId)).get(0).getId());
        assertEquals(empowerId, fetcher.findPayloads(MsgTypeEnum.CentralPubkeyEmpowerMsg, List.of(empowerId)).get(0).getId());
        assertEquals(centralLockedId, fetcher.findPayloads(MsgTypeEnum.CentralPubkeyLockedMsg, List.of(centralLockedId)).get(0).getId());
        assertEquals(flowLockedId, fetcher.findPayloads(MsgTypeEnum.FlowNodeLockedMsg, List.of(flowLockedId)).get(0).getId());
        assertEquals(recordId, fetcher.findPayloads(MsgTypeEnum.TransactionRecordMsg, List.of(recordId)).get(0).getId());
        assertEquals(mountId, fetcher.findPayloads(MsgTypeEnum.TransactionMountMsg, List.of(mountId)).get(0).getId());

        verify(flowNodeRegisterMsgRepository).findPayloadByIdIn(List.of(registerId));
        verify(centralPubkeyEmpowerMsgRepository).findPayloadByIdIn(List.of(empowerId));
        verify(centralPubkeyLockedMsgRepository).findPayloadByIdIn(List.of(centralLockedId));
        verify(flowNodeLockedMsgRepository).findPayloadByIdIn(List.of(flowLockedId));
        verify(transactionRecordMsgRepository).findPayloadByIdIn(List.of(recordId));
        verify(transactionMountMsgRepository).findPayloadByIdIn(List.of(mountId));
    }

    @Test
    void preservesSelectedMessageOrderWhenRepositoryReturnsUnorderedPayloads() {
        UUID firstId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID secondId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        when(flowNodeRegisterMsgRepository.findPayloadByIdIn(List.of(firstId, secondId)))
                .thenReturn(List.of(payload(secondId), payload(firstId)));

        List<MessagePayloadProjection> payloads = fetcher.findPayloads(
                MsgTypeEnum.FlowNodeRegisterMsg,
                List.of(firstId, secondId)
        );

        assertEquals(List.of(firstId, secondId), payloads.stream().map(MessagePayloadProjection::getId).toList());
    }

    private static List<MessagePayloadProjection> payloads(UUID id) {
        return List.of(payload(id));
    }

    private static MessagePayloadProjection payload(UUID id) {
        return new TestMessagePayloadProjection(id, new byte[] {1}, new byte[] {2});
    }

    private record TestMessagePayloadProjection(UUID id, byte[] rawBytes, byte[] txid) implements MessagePayloadProjection {

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public byte[] getRawBytes() {
            return rawBytes;
        }

        @Override
        public byte[] getTxid() {
            return txid;
        }
    }
}
