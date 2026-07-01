package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

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
        return samePersistentFlowNode(consumeChain.getStart(), consumeChain.getEnd());
    }

    private boolean samePersistentFlowNode(FlowNodeRegisterMsg start, FlowNodeRegisterMsg end) {
        UUID startId = start.getId();
        return startId != null && startId.equals(end.getId());
    }
}
