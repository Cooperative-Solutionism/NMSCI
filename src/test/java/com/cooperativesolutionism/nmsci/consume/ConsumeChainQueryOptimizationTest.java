package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.service.impl.ConsumeChainServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumeChainQueryOptimizationTest {

    @Test
    void saveConsumeChainLoadsMountedChainEdgesInOneBatch() {
        ConsumeChainServiceImpl service = new ConsumeChainServiceImpl();
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainAllocator allocator = mock(ConsumeChainAllocator.class);
        ConsumeChainPersistenceService persistenceService = mock(ConsumeChainPersistenceService.class);
        inject(service, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class), allocator, persistenceService);

        byte[] sourcePubkey = pubkey(1);
        byte[] targetPubkey = pubkey(2);
        FlowNodeRegisterMsg source = node("11111111-1111-1111-1111-111111111111", sourcePubkey);
        FlowNodeRegisterMsg target = node("22222222-2222-2222-2222-222222222222", targetPubkey);
        TransactionMountMsg mount = mount(sourcePubkey);
        TransactionRecordMsg record = record(targetPubkey);
        ConsumeChain firstChain = chain("33333333-3333-3333-3333-333333333333", source);
        ConsumeChain secondChain = chain("44444444-4444-4444-4444-444444444444", source);
        ConsumeChainEdge firstEdge = edge(firstChain, 10L);
        ConsumeChainEdge secondEdge = edge(secondChain, 20L);
        ConsumeChainAllocationPlan plan = new ConsumeChainAllocationPlan();

        when(flowNodeRepository.findFirstByFlowNodePubkey(sourcePubkey)).thenReturn(source);
        when(flowNodeRepository.findFirstByFlowNodePubkey(targetPubkey)).thenReturn(target);
        when(chainRepository.findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(source, record.getCurrencyType()))
                .thenReturn(List.of(firstChain, secondChain));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(firstChain, secondChain)))
                .thenReturn(List.of(firstEdge, secondEdge));
        when(allocator.allocate(
                eq(mount),
                eq(record),
                eq(source),
                eq(target),
                argThat(candidates ->
                        candidates.size() == 2
                                && candidates.get(0).edges().equals(List.of(firstEdge))
                                && candidates.get(1).edges().equals(List.of(secondEdge))
                )
        )).thenReturn(plan);

        service.saveConsumeChain(mount, record);

        verify(edgeRepository).findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(firstChain, secondChain));
        verify(edgeRepository, never()).findByChain(any());
        verify(persistenceService).save(plan);
    }

    @Test
    void getConsumeChainByStartLoadsEdgesForAllChainsInOneBatch() {
        ConsumeChainServiceImpl service = new ConsumeChainServiceImpl();
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        inject(service, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class), mock(ConsumeChainAllocator.class), mock(ConsumeChainPersistenceService.class));

        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111", pubkey(1));
        ConsumeChain firstChain = chain("22222222-2222-2222-2222-222222222222", start);
        ConsumeChain secondChain = chain("33333333-3333-3333-3333-333333333333", start);
        ConsumeChainEdge firstEdge = edge(firstChain, 10L);
        ConsumeChainEdge secondEdge = edge(secondChain, 20L);
        Pageable pageable = PageRequest.of(0, 50);

        when(flowNodeRepository.findById(start.getId())).thenReturn(Optional.of(start));
        when(chainRepository.findByStart(start, pageable))
                .thenReturn(new SliceImpl<>(List.of(firstChain, secondChain), pageable, false));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(firstChain, secondChain)))
                .thenReturn(List.of(firstEdge, secondEdge));

        Slice<ConsumeChainResponseDTO> response = service.getConsumeChainByStart(start.getId(), pageable);

        assertEquals(2, response.getNumberOfElements());
        assertSame(firstEdge, response.getContent().get(0).getConsumeChainEdges().get(0));
        assertSame(secondEdge, response.getContent().get(1).getConsumeChainEdges().get(0));
        verify(edgeRepository).findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(firstChain, secondChain));
        verify(edgeRepository, never()).findByChainOrderByRelatedTransactionMountTimestampAsc(any());
    }

    @Test
    void getConsumeChainByMountedTransactionReadsDistinctChainsDirectly() {
        ConsumeChainServiceImpl service = new ConsumeChainServiceImpl();
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        TransactionMountMsgRepository mountRepository = mock(TransactionMountMsgRepository.class);
        inject(service, mock(FlowNodeRegisterMsgRepository.class), chainRepository, edgeRepository, mountRepository, mock(ConsumeChainAllocator.class), mock(ConsumeChainPersistenceService.class));

        TransactionMountMsg mount = mount(pubkey(1));
        ConsumeChain chain = chain("11111111-1111-1111-1111-111111111111", node("22222222-2222-2222-2222-222222222222", pubkey(2)));
        ConsumeChainEdge edge = edge(chain, 10L);
        Pageable pageable = PageRequest.of(0, 50);

        when(mountRepository.findById(mount.getId())).thenReturn(Optional.of(mount));
        when(edgeRepository.findDistinctChainsByRelatedTransactionMount(mount, pageable))
                .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(chain))).thenReturn(List.of(edge));

        Slice<ConsumeChainResponseDTO> response = service.getConsumeChainByMountedTransaction(mount.getId(), pageable);

        assertEquals(1, response.getNumberOfElements());
        assertSame(chain, response.getContent().get(0).getConsumeChain());
        verify(edgeRepository).findDistinctChainsByRelatedTransactionMount(mount, pageable);
        verify(edgeRepository, never()).findByRelatedTransactionMount(any());
        verify(chainRepository, never()).findById(any());
    }

    private static void inject(
            ConsumeChainServiceImpl service,
            FlowNodeRegisterMsgRepository flowNodeRepository,
            ConsumeChainRepository chainRepository,
            ConsumeChainEdgeRepository edgeRepository,
            TransactionMountMsgRepository mountRepository,
            ConsumeChainAllocator allocator,
            ConsumeChainPersistenceService persistenceService
    ) {
        ReflectionTestUtils.setField(service, "flowNodeRegisterMsgRepository", flowNodeRepository);
        ReflectionTestUtils.setField(service, "consumeChainRepository", chainRepository);
        ReflectionTestUtils.setField(service, "consumeChainEdgeRepository", edgeRepository);
        ReflectionTestUtils.setField(service, "transactionMountMsgRepository", mountRepository);
        ReflectionTestUtils.setField(service, "consumeChainAllocator", allocator);
        ReflectionTestUtils.setField(service, "consumeChainPersistenceService", persistenceService);
    }

    private static FlowNodeRegisterMsg node(String id, byte[] pubkey) {
        FlowNodeRegisterMsg node = new FlowNodeRegisterMsg();
        node.setId(UUID.fromString(id));
        node.setFlowNodePubkey(pubkey);
        return node;
    }

    private static ConsumeChain chain(String id, FlowNodeRegisterMsg end) {
        ConsumeChain chain = new ConsumeChain();
        chain.setId(UUID.fromString(id));
        chain.setStart(end);
        chain.setEnd(end);
        chain.setAmount(100L);
        chain.setCurrencyType((short) 1);
        chain.setTailMountTimestamp(1L);
        return chain;
    }

    private static ConsumeChainEdge edge(ConsumeChain chain, long amount) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setId(UUID.randomUUID());
        edge.setChain(chain);
        edge.setAmount(amount);
        edge.setRelatedTransactionMountTimestamp(amount);
        return edge;
    }

    private static TransactionMountMsg mount(byte[] sourcePubkey) {
        TransactionMountMsg mount = new TransactionMountMsg();
        mount.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        mount.setFlowNodePubkey(sourcePubkey);
        return mount;
    }

    private static TransactionRecordMsg record(byte[] targetPubkey) {
        TransactionRecordMsg record = new TransactionRecordMsg();
        record.setFlowNodePubkey(targetPubkey);
        record.setCurrencyType((short) 1);
        record.setAmount(100L);
        return record;
    }

    private static byte[] pubkey(int seed) {
        byte[] pubkey = new byte[33];
        pubkey[0] = (byte) seed;
        return pubkey;
    }
}
