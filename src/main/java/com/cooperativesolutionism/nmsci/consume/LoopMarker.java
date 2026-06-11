package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoopMarker {

    public void markChain(ConsumeChain consumeChain) {
        consumeChain.setIsLoop(isLoop(consumeChain));
    }

    public void markEdges(List<ConsumeChainEdge> consumeChainEdges) {
        for (ConsumeChainEdge consumeChainEdge : consumeChainEdges) {
            consumeChainEdge.setIsLoop(isLoop(consumeChainEdge.getChain()));
        }
    }

    private boolean isLoop(ConsumeChain consumeChain) {
        return consumeChain.getStart().equals(consumeChain.getEnd());
    }
}
