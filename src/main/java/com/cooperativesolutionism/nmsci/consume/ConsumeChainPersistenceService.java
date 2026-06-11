package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConsumeChainPersistenceService {

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainEdgeRepository consumeChainEdgeRepository;

    @Resource
    private LoopMarker loopMarker;

    public void save(ConsumeChainAllocationPlan plan) {
        Map<ConsumeChain, ConsumeChain> savedChains = new IdentityHashMap<>();

        for (ConsumeChain consumeChain : plan.chainsToSave()) {
            loopMarker.markChain(consumeChain);
            savedChains.put(consumeChain, consumeChainRepository.save(consumeChain));
        }

        for (List<ConsumeChainEdge> edgeBatch : plan.edgeBatchesToSave()) {
            for (ConsumeChainEdge consumeChainEdge : edgeBatch) {
                ConsumeChain savedChain = savedChains.get(consumeChainEdge.getChain());
                if (savedChain != null) {
                    consumeChainEdge.setChain(savedChain);
                }
            }
            loopMarker.markEdges(edgeBatch);
            consumeChainEdgeRepository.saveAll(edgeBatch);
        }
    }
}
