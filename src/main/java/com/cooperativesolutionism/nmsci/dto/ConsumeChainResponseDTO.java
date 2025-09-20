package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;

import java.util.List;

public class ConsumeChainResponseDTO {

    private ConsumeChain consumeChain;

    private List<ConsumeChainEdge> consumeChainEdges;

    public ConsumeChain getConsumeChain() {
        return consumeChain;
    }

    public void setConsumeChain(ConsumeChain consumeChain) {
        this.consumeChain = consumeChain;
    }

    public List<ConsumeChainEdge> getConsumeChainEdges() {
        return consumeChainEdges;
    }

    public void setConsumeChainEdges(List<ConsumeChainEdge> consumeChainEdges) {
        this.consumeChainEdges = consumeChainEdges;
    }

}
