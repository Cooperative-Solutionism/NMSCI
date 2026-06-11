package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopMarkerTest {

    private final LoopMarker loopMarker = new LoopMarker();

    @Test
    void startEqualToEndMarksChainAndEdgesAsLoop() {
        FlowNodeRegisterMsg node = node("11111111-1111-1111-1111-111111111111");
        ConsumeChain chain = chain(node, node);
        ConsumeChainEdge edge = edge(chain);

        loopMarker.markChain(chain);
        loopMarker.markEdges(List.of(edge));

        assertTrue(chain.getIsLoop());
        assertTrue(edge.getIsLoop());
    }

    @Test
    void differentStartAndEndMarksChainAndEdgesAsNotLoop() {
        ConsumeChain chain = chain(
                node("11111111-1111-1111-1111-111111111111"),
                node("22222222-2222-2222-2222-222222222222")
        );
        ConsumeChainEdge edge = edge(chain);

        loopMarker.markChain(chain);
        loopMarker.markEdges(List.of(edge));

        assertFalse(chain.getIsLoop());
        assertFalse(edge.getIsLoop());
    }

    private static FlowNodeRegisterMsg node(String id) {
        FlowNodeRegisterMsg node = new FlowNodeRegisterMsg();
        node.setId(UUID.fromString(id));
        return node;
    }

    private static ConsumeChain chain(FlowNodeRegisterMsg start, FlowNodeRegisterMsg end) {
        ConsumeChain chain = new ConsumeChain();
        chain.setStart(start);
        chain.setEnd(end);
        return chain;
    }

    private static ConsumeChainEdge edge(ConsumeChain chain) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setChain(chain);
        return edge;
    }
}
