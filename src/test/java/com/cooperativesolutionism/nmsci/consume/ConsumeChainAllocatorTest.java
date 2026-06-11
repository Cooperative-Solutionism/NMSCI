package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeChainAllocatorTest {

    private static final short CURRENCY_TYPE = 1;
    private static final long MOUNT_TIMESTAMP = 1_700_000_000_000_000L;

    private final ConsumeChainAllocator allocator = new ConsumeChainAllocator();

    @Test
    void remainingAmountEqualToChainAmountExtendsExistingChain() {
        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111");
        FlowNodeRegisterMsg source = node("22222222-2222-2222-2222-222222222222");
        FlowNodeRegisterMsg target = node("33333333-3333-3333-3333-333333333333");
        ConsumeChain chain = chain(start, source, 100L, 10L);
        ConsumeChainEdge existingEdge = edge(start, source, chain, 100L, 10L);

        ConsumeChainAllocationPlan plan = allocator.allocate(
                mount(source),
                record(target, 100L),
                source,
                target,
                List.of(new ConsumeChainAllocationCandidate(chain, List.of(existingEdge)))
        );

        assertEquals(1, plan.chainsToSave().size());
        assertSame(chain, plan.chainsToSave().get(0));
        assertSame(target, chain.getEnd());
        assertEquals(100L, chain.getAmount());
        assertEquals(MOUNT_TIMESTAMP, chain.getTailMountTimestamp());

        List<ConsumeChainEdge> savedEdges = onlyEdgeBatch(plan);
        assertEquals(2, savedEdges.size());
        assertSame(existingEdge, savedEdges.get(0));
        assertEdge(savedEdges.get(1), source, target, chain, 100L, MOUNT_TIMESTAMP);
    }

    @Test
    void remainingAmountGreaterThanChainAmountExtendsChainAndCreatesRestChain() {
        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111");
        FlowNodeRegisterMsg source = node("22222222-2222-2222-2222-222222222222");
        FlowNodeRegisterMsg target = node("33333333-3333-3333-3333-333333333333");
        ConsumeChain chain = chain(start, source, 60L, 10L);
        ConsumeChainEdge existingEdge = edge(start, source, chain, 60L, 10L);

        ConsumeChainAllocationPlan plan = allocator.allocate(
                mount(source),
                record(target, 100L),
                source,
                target,
                List.of(new ConsumeChainAllocationCandidate(chain, List.of(existingEdge)))
        );

        assertEquals(2, plan.chainsToSave().size());
        assertSame(chain, plan.chainsToSave().get(0));
        assertSame(target, chain.getEnd());

        ConsumeChain restChain = plan.chainsToSave().get(1);
        assertSame(source, restChain.getStart());
        assertSame(target, restChain.getEnd());
        assertEquals(40L, restChain.getAmount());
        assertEquals(CURRENCY_TYPE, restChain.getCurrencyType());
        assertEquals(MOUNT_TIMESTAMP, restChain.getTailMountTimestamp());

        assertEquals(2, plan.edgeBatchesToSave().size());
        assertEdge(plan.edgeBatchesToSave().get(0).get(1), source, target, chain, 60L, MOUNT_TIMESTAMP);
        assertEdge(plan.edgeBatchesToSave().get(1).get(0), source, target, restChain, 40L, MOUNT_TIMESTAMP);
    }

    @Test
    void remainingAmountLessThanChainAmountSplitsExistingChain() {
        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111");
        FlowNodeRegisterMsg source = node("22222222-2222-2222-2222-222222222222");
        FlowNodeRegisterMsg target = node("33333333-3333-3333-3333-333333333333");
        ConsumeChain chain = chain(start, source, 100L, 10L);
        ConsumeChainEdge existingEdge = edge(start, source, chain, 100L, 10L);

        ConsumeChainAllocationPlan plan = allocator.allocate(
                mount(source),
                record(target, 40L),
                source,
                target,
                List.of(new ConsumeChainAllocationCandidate(chain, List.of(existingEdge)))
        );

        assertEquals(2, plan.chainsToSave().size());
        ConsumeChain splitChain = plan.chainsToSave().get(0);
        assertSame(start, splitChain.getStart());
        assertSame(target, splitChain.getEnd());
        assertEquals(40L, splitChain.getAmount());
        assertEquals(MOUNT_TIMESTAMP, splitChain.getTailMountTimestamp());

        assertSame(chain, plan.chainsToSave().get(1));
        assertSame(source, chain.getEnd());
        assertEquals(60L, chain.getAmount());
        assertEquals(10L, chain.getTailMountTimestamp());

        assertEquals(3, plan.edgeBatchesToSave().size());
        assertEdge(plan.edgeBatchesToSave().get(0).get(0), source, target, splitChain, 40L, MOUNT_TIMESTAMP);
        assertSame(existingEdge, plan.edgeBatchesToSave().get(1).get(0));
        assertEquals(60L, existingEdge.getAmount());
        assertEdge(plan.edgeBatchesToSave().get(2).get(0), start, source, splitChain, 40L, 10L);
    }

    @Test
    void multipleChainsAreConsumedContinuously() {
        FlowNodeRegisterMsg firstStart = node("11111111-1111-1111-1111-111111111111");
        FlowNodeRegisterMsg secondStart = node("22222222-2222-2222-2222-222222222222");
        FlowNodeRegisterMsg source = node("33333333-3333-3333-3333-333333333333");
        FlowNodeRegisterMsg target = node("44444444-4444-4444-4444-444444444444");
        ConsumeChain firstChain = chain(firstStart, source, 50L, 10L);
        ConsumeChain secondChain = chain(secondStart, source, 30L, 20L);
        ConsumeChainEdge firstEdge = edge(firstStart, source, firstChain, 50L, 10L);
        ConsumeChainEdge secondEdge = edge(secondStart, source, secondChain, 30L, 20L);

        ConsumeChainAllocationPlan plan = allocator.allocate(
                mount(source),
                record(target, 70L),
                source,
                target,
                List.of(
                        new ConsumeChainAllocationCandidate(firstChain, List.of(firstEdge)),
                        new ConsumeChainAllocationCandidate(secondChain, List.of(secondEdge))
                )
        );

        assertEquals(3, plan.chainsToSave().size());
        assertSame(target, firstChain.getEnd());
        assertEquals(50L, firstChain.getAmount());
        assertSame(source, secondChain.getEnd());
        assertEquals(10L, secondChain.getAmount());

        ConsumeChain splitChain = plan.chainsToSave().stream()
                .filter(savedChain -> savedChain != firstChain && savedChain != secondChain)
                .findFirst()
                .orElseThrow();
        assertSame(secondStart, splitChain.getStart());
        assertSame(target, splitChain.getEnd());
        assertEquals(20L, splitChain.getAmount());

        assertTrue(plan.chainsToSave().stream().noneMatch(savedChain ->
                savedChain.getStart() == source && savedChain.getAmount().equals(20L)
        ));
    }

    private static List<ConsumeChainEdge> onlyEdgeBatch(ConsumeChainAllocationPlan plan) {
        assertEquals(1, plan.edgeBatchesToSave().size());
        return plan.edgeBatchesToSave().get(0);
    }

    private static FlowNodeRegisterMsg node(String id) {
        FlowNodeRegisterMsg node = new FlowNodeRegisterMsg();
        node.setId(UUID.fromString(id));
        return node;
    }

    private static TransactionMountMsg mount(FlowNodeRegisterMsg source) {
        TransactionMountMsg mount = new TransactionMountMsg();
        mount.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        mount.setFlowNodePubkey(new byte[33]);
        mount.setConfirmTimestamp(MOUNT_TIMESTAMP);
        return mount;
    }

    private static TransactionRecordMsg record(FlowNodeRegisterMsg target, long amount) {
        TransactionRecordMsg record = new TransactionRecordMsg();
        record.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        record.setFlowNodePubkey(new byte[33]);
        record.setAmount(amount);
        record.setCurrencyType(CURRENCY_TYPE);
        return record;
    }

    private static ConsumeChain chain(FlowNodeRegisterMsg start, FlowNodeRegisterMsg end, long amount, long tailMountTimestamp) {
        ConsumeChain chain = new ConsumeChain();
        chain.setId(UUID.randomUUID());
        chain.setStart(start);
        chain.setEnd(end);
        chain.setAmount(amount);
        chain.setCurrencyType(CURRENCY_TYPE);
        chain.setTailMountTimestamp(tailMountTimestamp);
        return chain;
    }

    private static ConsumeChainEdge edge(
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            ConsumeChain chain,
            long amount,
            long mountTimestamp
    ) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setId(UUID.randomUUID());
        edge.setSource(source);
        edge.setTarget(target);
        edge.setAmount(amount);
        edge.setCurrencyType(CURRENCY_TYPE);
        edge.setChain(chain);
        edge.setRelatedTransactionRecord(new TransactionRecordMsg());
        edge.setRelatedTransactionMount(new TransactionMountMsg());
        edge.setRelatedTransactionMountTimestamp(mountTimestamp);
        return edge;
    }

    private static void assertEdge(
            ConsumeChainEdge edge,
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            ConsumeChain chain,
            long amount,
            long mountTimestamp
    ) {
        assertSame(source, edge.getSource());
        assertSame(target, edge.getTarget());
        assertSame(chain, edge.getChain());
        assertEquals(amount, edge.getAmount());
        assertEquals(CURRENCY_TYPE, edge.getCurrencyType());
        assertEquals(mountTimestamp, edge.getRelatedTransactionMountTimestamp());
    }
}
