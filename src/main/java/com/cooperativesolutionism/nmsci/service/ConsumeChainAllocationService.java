package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocationCandidate;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocationPlan;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocator;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainPersistenceService;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainSupport;
import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumeChainAllocationService {

    @Resource
    private ConsumeChainSupport consumeChainSupport;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainAllocator consumeChainAllocator;

    @Resource
    private ConsumeChainPersistenceService consumeChainPersistenceService;

    @Resource
    private NmsciMetrics nmsciMetrics;

    @Transactional
    public void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg) {
        nmsciMetrics.timeConsumeChainAllocation(() -> {
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
        });
    }

    private List<ConsumeChain> getMountChainsForAllocation(
            FlowNodeRegisterMsg source,
            Short currencyType,
            long transactionAmount
    ) {
        // 单条窗口累计和查询取「刚好够」的开放链最小前缀并加 FOR UPDATE 悲观写锁；
        // 不足部分由分配器新建链承接。
        return consumeChainRepository.lockOpenChainsForAllocation(source.getId(), currencyType, transactionAmount);
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
