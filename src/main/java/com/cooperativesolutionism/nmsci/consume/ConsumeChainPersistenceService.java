package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        List<ConsumeChain> chains = new ArrayList<>(plan.chainsToSave());
        Map<ConsumeChain, ConsumeChain> savedChains = new IdentityHashMap<>();

        for (ConsumeChain consumeChain : chains) {
            loopMarker.markChain(consumeChain);
        }

        List<ConsumeChain> savedChainList = consumeChainRepository.saveAll(chains);
        for (int i = 0; i < chains.size(); i++) {
            savedChains.put(chains.get(i), savedChainList.get(i));
        }

        List<ConsumeChainEdge> edges = new ArrayList<>();
        for (List<ConsumeChainEdge> edgeBatch : plan.edgeBatchesToSave()) {
            for (ConsumeChainEdge consumeChainEdge : edgeBatch) {
                ConsumeChain savedChain = savedChains.get(consumeChainEdge.getChain());
                if (savedChain != null) {
                    consumeChainEdge.setChain(savedChain);
                }
                edges.add(consumeChainEdge);
            }
        }

        loopMarker.markEdges(edges);
        consumeChainEdgeRepository.saveAll(edges);
    }
}
