package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumeChainPersistenceServiceTest {

    @Test
    void savesChainsAndEdgesInBatches() {
        ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
        ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
        ConsumeChainPersistenceService service = new ConsumeChainPersistenceService();
        ReflectionTestUtils.setField(service, "consumeChainRepository", chainRepository);
        ReflectionTestUtils.setField(service, "consumeChainEdgeRepository", edgeRepository);
        ReflectionTestUtils.setField(service, "loopMarker", new LoopMarker());

        ConsumeChain firstChain = chain("11111111-1111-1111-1111-111111111111");
        ConsumeChain secondChain = chain("22222222-2222-2222-2222-222222222222");
        ConsumeChainEdge firstEdge = edge(firstChain);
        ConsumeChainEdge secondEdge = edge(secondChain);
        ConsumeChainAllocationPlan plan = new ConsumeChainAllocationPlan();
        plan.saveChain(firstChain);
        plan.saveChain(secondChain);
        plan.saveEdges(List.of(firstEdge));
        plan.saveEdges(List.of(secondEdge));
        when(chainRepository.saveAll(List.of(firstChain, secondChain))).thenReturn(List.of(firstChain, secondChain));

        service.save(plan);

        verify(chainRepository).saveAll(List.of(firstChain, secondChain));
        verify(edgeRepository).saveAll(List.of(firstEdge, secondEdge));
    }

    private static ConsumeChain chain(String id) {
        FlowNodeRegisterMsg node = new FlowNodeRegisterMsg();
        node.setId(UUID.randomUUID());
        ConsumeChain chain = new ConsumeChain();
        chain.setId(UUID.fromString(id));
        chain.setStart(node);
        chain.setEnd(node);
        return chain;
    }

    private static ConsumeChainEdge edge(ConsumeChain chain) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setChain(chain);
        return edge;
    }
}
