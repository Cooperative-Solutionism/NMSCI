package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.service.ConsumeChainAllocationService;
import com.cooperativesolutionism.nmsci.service.ConsumeChainQueryService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void saveConsumeChainLocksOpenChainsInOneWindowedQueryAndLoadsSelectedEdgesInOneBatch() {
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainAllocator allocator = mock(ConsumeChainAllocator.class);
        ConsumeChainPersistenceService persistenceService = mock(ConsumeChainPersistenceService.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainAllocationService service = allocationService(support, chainRepository, allocator, persistenceService);

        byte[] sourcePubkey = pubkey(1);
        byte[] targetPubkey = pubkey(2);
        FlowNodeRegisterMsg source = node("11111111-1111-1111-1111-111111111111", sourcePubkey);
        FlowNodeRegisterMsg target = node("22222222-2222-2222-2222-222222222222", targetPubkey);
        TransactionMountMsg mount = mount(sourcePubkey);
        TransactionRecordMsg record = record(targetPubkey, 101L);
        ConsumeChain firstChain = chain(1, source, 100L);
        ConsumeChain secondChain = chain(2, source, 1L);
        List<ConsumeChain> selectedChains = List.of(firstChain, secondChain);
        ConsumeChainEdge firstEdge = edge(firstChain, 10L);
        ConsumeChainEdge lastEdge = edge(secondChain, 20L);
        ConsumeChainAllocationPlan plan = new ConsumeChainAllocationPlan();

        when(flowNodeRepository.findFirstByFlowNodePubkey(sourcePubkey)).thenReturn(source);
        when(flowNodeRepository.findFirstByFlowNodePubkey(targetPubkey)).thenReturn(target);
        when(chainRepository.lockOpenChainsForAllocation(source.getId(), record.getCurrencyType(), record.getAmount(), target.getId()))
                .thenReturn(selectedChains);
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(selectedChains))
                .thenReturn(List.of(firstEdge, lastEdge));
        when(allocator.allocate(
                eq(mount),
                eq(record),
                eq(source),
                eq(target),
                any()
        )).thenReturn(plan);

        service.saveConsumeChain(mount, record);

        verify(chainRepository).lockOpenChainsForAllocation(source.getId(), record.getCurrencyType(), record.getAmount(), target.getId());
        verify(edgeRepository).findByChainInOrderByRelatedTransactionMountTimestampAsc(selectedChains);
        verify(edgeRepository, never()).findByChain(any());
        verify(allocator).allocate(
                eq(mount),
                eq(record),
                eq(source),
                eq(target),
                argThat(candidatesMatchChains(selectedChains))
        );
        verify(persistenceService).save(plan);
    }

    @Test
    void getConsumeChainByStartLoadsEdgesForAllChainsInOneBatch() {
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainQueryService service = queryService(support, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class));

        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111", pubkey(1));
        ConsumeChain firstChain = chain("22222222-2222-2222-2222-222222222222", start);
        ConsumeChain secondChain = chain("33333333-3333-3333-3333-333333333333", start);
        ConsumeChainEdge firstEdge = edge(firstChain, 10L);
        ConsumeChainEdge secondEdge = edge(secondChain, 20L);
        Pageable pageable = PageRequest.of(0, 50);

        when(flowNodeRepository.findById(start.getId())).thenReturn(Optional.of(start));
        when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, start, null, pageable))
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
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        TransactionMountMsgRepository mountRepository = mock(TransactionMountMsgRepository.class);
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainQueryService service = queryService(support, flowNodeRepository, chainRepository, edgeRepository, mountRepository);

        TransactionMountMsg mount = mount(pubkey(1));
        ConsumeChain chain = chain("11111111-1111-1111-1111-111111111111", node("22222222-2222-2222-2222-222222222222", pubkey(2)));
        ConsumeChainEdge edge = edge(chain, 10L);
        Pageable pageable = PageRequest.of(0, 50);

        when(mountRepository.findById(mount.getId())).thenReturn(Optional.of(mount));
        when(chainRepository.findDistinctByRelatedTransactionMount(mount, pageable))
                .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(chain))).thenReturn(List.of(edge));

        Slice<ConsumeChainResponseDTO> response = service.getConsumeChainByMountedTransaction(mount.getId(), pageable);

        assertEquals(1, response.getNumberOfElements());
        assertSame(chain, response.getContent().get(0).getConsumeChain());
        verify(chainRepository).findDistinctByRelatedTransactionMount(mount, pageable);
        verify(edgeRepository, never()).findByRelatedTransactionMount(any());
        verify(chainRepository, never()).findById(any());
    }

    @Test
    void getConsumeChainByNodeLoadsAllContainingChainsAndEdgesInOneBatch() {
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainQueryService service = queryService(support, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class));

        FlowNodeRegisterMsg node = node("11111111-1111-1111-1111-111111111111", pubkey(1));
        ConsumeChain firstChain = chain("22222222-2222-2222-2222-222222222222", node);
        ConsumeChain secondChain = chain("33333333-3333-3333-3333-333333333333", node);
        ConsumeChainEdge firstEdge = edge(firstChain, 10L);
        ConsumeChainEdge secondEdge = edge(secondChain, 20L);
        Pageable pageable = PageRequest.of(0, 50);

        when(flowNodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.NODE, node, null, pageable))
                .thenReturn(new SliceImpl<>(List.of(firstChain, secondChain), pageable, false));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(firstChain, secondChain)))
                .thenReturn(List.of(firstEdge, secondEdge));

        Slice<ConsumeChainResponseDTO> response = service.getConsumeChainByNode(node.getId(), pageable);

        assertEquals(2, response.getNumberOfElements());
        assertSame(firstEdge, response.getContent().get(0).getConsumeChainEdges().get(0));
        assertSame(secondEdge, response.getContent().get(1).getConsumeChainEdges().get(0));
        verify(chainRepository).findByNodeFilter(ConsumeChainNodeFilter.NODE, node, null, pageable);
        verify(edgeRepository).findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(firstChain, secondChain));
        verify(edgeRepository, never()).findByChainOrderByRelatedTransactionMountTimestampAsc(any());
    }

    @Test
    void getConsumeChainByPubkeyUsesExactlyOnePubkeyFilterAndLoadsEdgesInOneBatch() {
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainQueryService service = queryService(support, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class));

        byte[] startPubkey = pubkey(1);
        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111", startPubkey);
        ConsumeChain chain = chain("22222222-2222-2222-2222-222222222222", start);
        ConsumeChainEdge edge = edge(chain, 10L);
        Pageable pageable = PageRequest.of(0, 50);

        when(flowNodeRepository.findFirstByFlowNodePubkey(startPubkey)).thenReturn(start);
        when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, start, true, pageable))
                .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(chain))).thenReturn(List.of(edge));

        Slice<ConsumeChainResponseDTO> response = service.getConsumeChainByPubkey(startPubkey, null, null, true, pageable);

        assertEquals(1, response.getNumberOfElements());
        assertSame(edge, response.getContent().get(0).getConsumeChainEdges().get(0));
        verify(flowNodeRepository).findFirstByFlowNodePubkey(startPubkey);
        verify(chainRepository).findByNodeFilter(ConsumeChainNodeFilter.START, start, true, pageable);
        verify(edgeRepository).findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(chain));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getConsumeChainByPubkey(startPubkey, pubkey(2), null, null, pageable)
        );
    }

    @Test
    void getConsumeChainByRelatedIdUsesExactlyOneIdFilterAndLoadsEdgesInOneBatch() {
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainQueryService service = queryService(support, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class));

        FlowNodeRegisterMsg node = node("11111111-1111-1111-1111-111111111111", pubkey(1));
        ConsumeChain chain = chain("22222222-2222-2222-2222-222222222222", node);
        ConsumeChainEdge edge = edge(chain, 10L);
        Pageable pageable = PageRequest.of(0, 50);

        when(flowNodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.NODE, node, true, pageable))
                .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
        when(edgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(chain))).thenReturn(List.of(edge));

        Slice<ConsumeChainResponseDTO> response = service.getConsumeChainByRelatedId(null, null, node.getId(), true, pageable);

        assertEquals(1, response.getNumberOfElements());
        assertSame(edge, response.getContent().get(0).getConsumeChainEdges().get(0));
        verify(flowNodeRepository).findById(node.getId());
        verify(chainRepository).findByNodeFilter(ConsumeChainNodeFilter.NODE, node, true, pageable);
        verify(edgeRepository).findByChainInOrderByRelatedTransactionMountTimestampAsc(List.of(chain));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getConsumeChainByRelatedId(node.getId(), node.getId(), null, null, pageable)
        );
    }

    @Test
    void nodeIdHelpersPreserveCurrentNullAndNotFoundMessages() {
        FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
        ConsumeChainQueryService service = queryService(support, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class));
        UUID missing = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Pageable pageable = PageRequest.of(0, 50);

        IllegalArgumentException startNull = assertThrows(
                IllegalArgumentException.class,
                () -> service.getConsumeChainByStart(null, pageable)
        );
        assertEquals("起点ID不能为空", startNull.getMessage());

        when(flowNodeRepository.findById(missing)).thenReturn(Optional.empty());
        NotFoundException startMissing = assertThrows(
                NotFoundException.class,
                () -> service.getConsumeChainByStart(missing, pageable)
        );
        assertEquals("起点ID不存在", startMissing.getMessage());

        IllegalArgumentException endNull = assertThrows(
                IllegalArgumentException.class,
                () -> service.getConsumeChainByEnd(null, pageable)
        );
        assertEquals("终点ID不能为空", endNull.getMessage());

        IllegalArgumentException nodeNull = assertThrows(
                IllegalArgumentException.class,
                () -> service.getConsumeChainByNode(null, pageable)
        );
        assertEquals("节点ID不能为空", nodeNull.getMessage());
    }

    private static ConsumeChainSupport support(
            FlowNodeRegisterMsgRepository flowNodeRepository,
            ConsumeChainEdgeRepository edgeRepository
    ) {
        return new ConsumeChainSupport(flowNodeRepository, edgeRepository);
    }

    private static ConsumeChainAllocationService allocationService(
            ConsumeChainSupport support,
            ConsumeChainRepository chainRepository,
            ConsumeChainAllocator allocator,
            ConsumeChainPersistenceService persistenceService
    ) {
        return new ConsumeChainAllocationService(
                support,
                chainRepository,
                allocator,
                persistenceService,
                new NmsciMetrics(new SimpleMeterRegistry(), mock(MsgAbstractService.class))
        );
    }

    private static ConsumeChainQueryService queryService(
            ConsumeChainSupport support,
            FlowNodeRegisterMsgRepository flowNodeRepository,
            ConsumeChainRepository chainRepository,
            ConsumeChainEdgeRepository edgeRepository,
            TransactionMountMsgRepository mountRepository
    ) {
        return new ConsumeChainQueryService(
                support,
                flowNodeRepository,
                chainRepository,
                edgeRepository,
                mountRepository
        );
    }

    private static FlowNodeRegisterMsg node(String id, byte[] pubkey) {
        FlowNodeRegisterMsg node = new FlowNodeRegisterMsg();
        node.setId(UUID.fromString(id));
        node.setFlowNodePubkey(pubkey);
        return node;
    }

    private static ConsumeChain chain(String id, FlowNodeRegisterMsg end) {
        return chain(id, end, 100L);
    }

    private static ConsumeChain chain(int suffix, FlowNodeRegisterMsg end, long amount) {
        return chain("00000000-0000-0000-0000-" + String.format("%012x", suffix), end, amount);
    }

    private static ConsumeChain chain(String id, FlowNodeRegisterMsg end, long amount) {
        ConsumeChain chain = new ConsumeChain();
        chain.setId(UUID.fromString(id));
        chain.setStart(end);
        chain.setEnd(end);
        chain.setAmount(amount);
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
        return record(targetPubkey, 100L);
    }

    private static TransactionRecordMsg record(byte[] targetPubkey, long amount) {
        TransactionRecordMsg record = new TransactionRecordMsg();
        record.setFlowNodePubkey(targetPubkey);
        record.setCurrencyType((short) 1);
        record.setAmount(amount);
        return record;
    }

    private static org.mockito.ArgumentMatcher<List<ConsumeChainAllocationCandidate>> candidatesMatchChains(List<ConsumeChain> chains) {
        return candidates -> {
            if (candidates == null || candidates.size() != chains.size()) {
                return false;
            }
            for (int i = 0; i < chains.size(); i++) {
                if (candidates.get(i).chain() != chains.get(i)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static byte[] pubkey(int seed) {
        byte[] pubkey = new byte[33];
        pubkey[0] = (byte) seed;
        return pubkey;
    }
}
