package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocationCandidate;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocationPlan;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocator;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainPersistenceService;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumeChainAllocationService {

    private static final int ALLOCATION_CHAIN_LOCK_BATCH_SIZE = 100;

    @Resource
    private ConsumeChainSupport consumeChainSupport;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainAllocator consumeChainAllocator;

    @Resource
    private ConsumeChainPersistenceService consumeChainPersistenceService;

    @Transactional
    public void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg) {
        FlowNodeRegisterMsg source = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(transactionMountMsg.getFlowNodePubkey(), "源");
        FlowNodeRegisterMsg target = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(transactionRecordMsg.getFlowNodePubkey(), "目标");

        List<ConsumeChain> mountChains = getMountChainsForAllocation(
                source,
                transactionRecordMsg.getCurrencyType(),
                transactionRecordMsg.getAmount()
        );

        ConsumeChainAllocationPlan plan = consumeChainAllocator.allocate(
                transactionMountMsg,
                transactionRecordMsg,
                source,
                target,
                getConsumeChainAllocationCandidates(mountChains)
        );
        consumeChainPersistenceService.save(plan);
    }

    private List<ConsumeChain> getMountChainsForAllocation(
            FlowNodeRegisterMsg source,
            Short currencyType,
            long transactionAmount
    ) {
        List<ConsumeChain> mountChains = new ArrayList<>();
        long remainingAmount = transactionAmount;
        int page = 0;

        while (remainingAmount > 0) {
            List<ConsumeChain> batch = consumeChainRepository.findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(
                    source,
                    currencyType,
                    PageRequest.of(page, ALLOCATION_CHAIN_LOCK_BATCH_SIZE)
            );
            if (batch.isEmpty()) {
                break;
            }

            for (ConsumeChain mountChain : batch) {
                mountChains.add(mountChain);
                remainingAmount -= mountChain.getAmount();
                if (remainingAmount <= 0) {
                    break;
                }
            }

            if (batch.size() < ALLOCATION_CHAIN_LOCK_BATCH_SIZE) {
                break;
            }
            page++;
        }

        return mountChains;
    }

    private List<ConsumeChainAllocationCandidate> getConsumeChainAllocationCandidates(List<ConsumeChain> mountChains) {
        Map<UUID, List<ConsumeChainEdge>> edgesByChainId = consumeChainSupport.getEdgesByChainId(mountChains);
        List<ConsumeChainAllocationCandidate> candidates = new ArrayList<>();

        for (ConsumeChain mountChain : mountChains) {
            candidates.add(new ConsumeChainAllocationCandidate(
                    mountChain,
                    edgesByChainId.getOrDefault(mountChain.getId(), List.of())
            ));
        }

        return candidates;
    }

}
