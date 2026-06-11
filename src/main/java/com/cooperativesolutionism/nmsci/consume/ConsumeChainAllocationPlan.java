package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConsumeChainAllocationPlan {

    private final List<ConsumeChain> chainsToSave = new ArrayList<>();

    private final List<List<ConsumeChainEdge>> edgeBatchesToSave = new ArrayList<>();

    void saveChain(ConsumeChain chain) {
        chainsToSave.add(chain);
    }

    void saveEdges(List<ConsumeChainEdge> edges) {
        if (!edges.isEmpty()) {
            edgeBatchesToSave.add(List.copyOf(edges));
        }
    }

    public List<ConsumeChain> chainsToSave() {
        return Collections.unmodifiableList(chainsToSave);
    }

    public List<List<ConsumeChainEdge>> edgeBatchesToSave() {
        return Collections.unmodifiableList(edgeBatchesToSave);
    }
}
