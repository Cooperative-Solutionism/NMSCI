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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumeChainAllocationService {

    private final ConsumeChainSupport consumeChainSupport;
    private final ConsumeChainRepository consumeChainRepository;
    private final ConsumeChainAllocator consumeChainAllocator;
    private final ConsumeChainPersistenceService consumeChainPersistenceService;
    private final NmsciMetrics nmsciMetrics;

    public ConsumeChainAllocationService(
            ConsumeChainSupport consumeChainSupport,
            ConsumeChainRepository consumeChainRepository,
            ConsumeChainAllocator consumeChainAllocator,
            ConsumeChainPersistenceService consumeChainPersistenceService,
            NmsciMetrics nmsciMetrics
    ) {
        this.consumeChainSupport = consumeChainSupport;
        this.consumeChainRepository = consumeChainRepository;
        this.consumeChainAllocator = consumeChainAllocator;
        this.consumeChainPersistenceService = consumeChainPersistenceService;
        this.nmsciMetrics = nmsciMetrics;
    }

    /**
     * 必须沿用调用方 {@code TransactionMountWriteService.saveAndAllocate} 的同一事务（默认 REQUIRED 传播）。
     * 切勿改为 {@code REQUIRES_NEW}：否则挂载落库与链分配将分属两个事务，破坏其同事务原子性与那里
     * 针对并发锁定/授权的二次状态校验（review #4），可能产生「校验通过但分配落在另一事务」的不一致。
     */
    @Transactional
    public void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg) {
        nmsciMetrics.timeConsumeChainAllocation(() -> {
            FlowNodeRegisterMsg source = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(transactionMountMsg.getFlowNodePubkey(), "源");
            FlowNodeRegisterMsg target = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(transactionRecordMsg.getFlowNodePubkey(), "目标");

            List<ConsumeChain> mountChains = getMountChainsForAllocation(
                    source,
                    target,
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
            FlowNodeRegisterMsg target,
            Short currencyType,
            long transactionAmount
    ) {
        // 单条窗口累计和查询取「刚好够」的开放链最小前缀并加 FOR UPDATE 悲观写锁；
        // 优先 start==target 的链（延伸/拆分后即成环），其次按时间；不足部分由分配器新建链承接。
        return consumeChainRepository.lockOpenChainsForAllocation(source.getId(), currencyType, transactionAmount, target.getId());
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
