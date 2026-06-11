package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConsumeChainAllocator {

    public ConsumeChainAllocationPlan allocate(
            TransactionMountMsg transactionMountMsg,
            TransactionRecordMsg transactionRecordMsg,
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            List<ConsumeChainAllocationCandidate> mountChains
    ) {
        ConsumeChainAllocationPlan plan = new ConsumeChainAllocationPlan();
        long restAmount = transactionRecordMsg.getAmount();

        for (ConsumeChainAllocationCandidate candidate : mountChains) {
            ConsumeChain mountChain = candidate.chain();
            long mountChainAmount = mountChain.getAmount();

            if (restAmount <= 0) {
                break;
            }

            if (restAmount >= mountChainAmount) {
                extendChain(plan, transactionMountMsg, transactionRecordMsg, source, target, candidate, mountChainAmount);
            }

            if (restAmount < mountChainAmount) {
                splitChain(plan, transactionMountMsg, transactionRecordMsg, source, target, candidate, restAmount, mountChainAmount);
            }

            restAmount -= mountChainAmount;
        }

        if (restAmount > 0) {
            createRestChain(plan, transactionMountMsg, transactionRecordMsg, source, target, restAmount);
        }

        return plan;
    }

    private void extendChain(
            ConsumeChainAllocationPlan plan,
            TransactionMountMsg transactionMountMsg,
            TransactionRecordMsg transactionRecordMsg,
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            ConsumeChainAllocationCandidate candidate,
            long mountChainAmount
    ) {
        ConsumeChain mountChain = candidate.chain();
        mountChain.setEnd(target);
        mountChain.setTailMountTimestamp(transactionMountMsg.getConfirmTimestamp());
        plan.saveChain(mountChain);

        List<ConsumeChainEdge> edges = new ArrayList<>(candidate.edges());
        edges.add(newEdge(
                transactionMountMsg,
                transactionRecordMsg,
                source,
                target,
                mountChain,
                mountChainAmount,
                transactionMountMsg.getConfirmTimestamp()
        ));
        plan.saveEdges(edges);
    }

    private void splitChain(
            ConsumeChainAllocationPlan plan,
            TransactionMountMsg transactionMountMsg,
            TransactionRecordMsg transactionRecordMsg,
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            ConsumeChainAllocationCandidate candidate,
            long restAmount,
            long mountChainAmount
    ) {
        ConsumeChain mountChain = candidate.chain();
        ConsumeChain splitChain = newChain(
                mountChain.getStart(),
                target,
                restAmount,
                transactionRecordMsg.getCurrencyType(),
                transactionMountMsg.getConfirmTimestamp()
        );
        plan.saveChain(splitChain);
        plan.saveEdges(List.of(newEdge(
                transactionMountMsg,
                transactionRecordMsg,
                source,
                target,
                splitChain,
                restAmount,
                transactionMountMsg.getConfirmTimestamp()
        )));

        mountChain.setAmount(mountChainAmount - restAmount);
        plan.saveChain(mountChain);

        List<ConsumeChainEdge> originEdges = new ArrayList<>(candidate.edges());
        List<ConsumeChainEdge> splitOriginEdges = new ArrayList<>();
        for (ConsumeChainEdge originEdge : originEdges) {
            originEdge.setAmount(mountChain.getAmount());
            splitOriginEdges.add(copyEdge(originEdge, splitChain, restAmount));
        }

        plan.saveEdges(originEdges);
        plan.saveEdges(splitOriginEdges);
    }

    private void createRestChain(
            ConsumeChainAllocationPlan plan,
            TransactionMountMsg transactionMountMsg,
            TransactionRecordMsg transactionRecordMsg,
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            long restAmount
    ) {
        ConsumeChain consumeChain = newChain(
                source,
                target,
                restAmount,
                transactionRecordMsg.getCurrencyType(),
                transactionMountMsg.getConfirmTimestamp()
        );
        plan.saveChain(consumeChain);
        plan.saveEdges(List.of(newEdge(
                transactionMountMsg,
                transactionRecordMsg,
                source,
                target,
                consumeChain,
                restAmount,
                transactionMountMsg.getConfirmTimestamp()
        )));
    }

    private ConsumeChain newChain(
            FlowNodeRegisterMsg start,
            FlowNodeRegisterMsg end,
            long amount,
            short currencyType,
            long tailMountTimestamp
    ) {
        ConsumeChain consumeChain = new ConsumeChain();
        consumeChain.setStart(start);
        consumeChain.setEnd(end);
        consumeChain.setAmount(amount);
        consumeChain.setCurrencyType(currencyType);
        consumeChain.setTailMountTimestamp(tailMountTimestamp);
        return consumeChain;
    }

    private ConsumeChainEdge newEdge(
            TransactionMountMsg transactionMountMsg,
            TransactionRecordMsg transactionRecordMsg,
            FlowNodeRegisterMsg source,
            FlowNodeRegisterMsg target,
            ConsumeChain chain,
            long amount,
            long mountTimestamp
    ) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setAmount(amount);
        edge.setCurrencyType(transactionRecordMsg.getCurrencyType());
        edge.setChain(chain);
        edge.setRelatedTransactionRecord(transactionRecordMsg);
        edge.setRelatedTransactionMount(transactionMountMsg);
        edge.setRelatedTransactionMountTimestamp(mountTimestamp);
        return edge;
    }

    private ConsumeChainEdge copyEdge(ConsumeChainEdge originEdge, ConsumeChain chain, long amount) {
        ConsumeChainEdge newEdge = new ConsumeChainEdge();
        newEdge.setSource(originEdge.getSource());
        newEdge.setTarget(originEdge.getTarget());
        newEdge.setAmount(amount);
        newEdge.setCurrencyType(originEdge.getCurrencyType());
        newEdge.setChain(chain);
        newEdge.setRelatedTransactionRecord(originEdge.getRelatedTransactionRecord());
        newEdge.setRelatedTransactionMount(originEdge.getRelatedTransactionMount());
        newEdge.setRelatedTransactionMountTimestamp(originEdge.getRelatedTransactionMountTimestamp());
        return newEdge;
    }
}
