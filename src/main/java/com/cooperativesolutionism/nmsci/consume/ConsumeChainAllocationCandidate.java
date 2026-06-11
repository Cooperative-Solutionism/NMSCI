package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;

import java.util.List;
import java.util.Objects;

public final class ConsumeChainAllocationCandidate {

    private final ConsumeChain chain;

    private final List<ConsumeChainEdge> edges;

    public ConsumeChainAllocationCandidate(ConsumeChain chain, List<ConsumeChainEdge> edges) {
        this.chain = Objects.requireNonNull(chain, "chain must not be null");
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges must not be null"));
    }

    public ConsumeChain chain() {
        return chain;
    }

    public List<ConsumeChainEdge> edges() {
        return edges;
    }
}
