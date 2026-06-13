package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocationCandidate;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocationPlan;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainAllocator;
import com.cooperativesolutionism.nmsci.consume.ConsumeChainPersistenceService;
import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.model.*;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumeChainServiceImpl implements ConsumeChainService {

    private static final int ALLOCATION_CHAIN_LOCK_BATCH_SIZE = 100;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainEdgeRepository consumeChainEdgeRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private ConsumeChainAllocator consumeChainAllocator;

    @Resource
    private ConsumeChainPersistenceService consumeChainPersistenceService;

    @Override
    @Transactional
    public void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg) {
        FlowNodeRegisterMsg source = getFlowNodeRegisterMsgByPubkey(transactionMountMsg.getFlowNodePubkey(), "源");
        FlowNodeRegisterMsg target = getFlowNodeRegisterMsgByPubkey(transactionRecordMsg.getFlowNodePubkey(), "目标");

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

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRateById(ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        if (!CurrencyTypeEnum.containsValue(returningFlowRateRequestDTO.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        ConsumeChainEdgeRepository.ReturningFlowRateAggregate aggregate = consumeChainEdgeRepository.aggregateReturningFlowRate(
                returningFlowRateRequestDTO.getSourceId(),
                returningFlowRateRequestDTO.getTargetId(),
                returningFlowRateRequestDTO.getCurrencyType(),
                returningFlowRateRequestDTO.getStartTime(),
                returningFlowRateRequestDTO.getEndTime()
        );

        double loopedAmount = amount(aggregate.getLoopedAmount());
        double unloopedAmount = amount(aggregate.getUnloopedAmount());
        double returningFlowRate = (loopedAmount + unloopedAmount) == 0 ? 0 : loopedAmount / (loopedAmount + unloopedAmount);

        return new ReturningFlowRateResponseDTO(
                returningFlowRate,
                loopedAmount,
                unloopedAmount,
                returningFlowRateRequestDTO.getCurrencyType()
        );
    }

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRateByTargetId(ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        if (!CurrencyTypeEnum.containsValue(returningFlowRateRequestDTO.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        ConsumeChainEdgeRepository.ReturningFlowRateAggregate aggregate = consumeChainEdgeRepository.aggregateReturningFlowRateByTarget(
                returningFlowRateRequestDTO.getTargetId(),
                returningFlowRateRequestDTO.getCurrencyType(),
                returningFlowRateRequestDTO.getStartTime(),
                returningFlowRateRequestDTO.getEndTime()
        );

        return new ReturningFlowRateResponseDTO(
                amount(aggregate.getLoopedAmount()),
                amount(aggregate.getUnloopedAmount()),
                returningFlowRateRequestDTO.getCurrencyType()
        );
    }

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRateByPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        FlowNodeRegisterMsg source = getFlowNodeRegisterMsgByPubkey(returningFlowRateRequestDTO.getSource(), "源");
        FlowNodeRegisterMsg target = getFlowNodeRegisterMsgByPubkey(returningFlowRateRequestDTO.getTarget(), "目标");

        returningFlowRateRequestDTO.setTargetId(target.getId());
        returningFlowRateRequestDTO.setSourceId(source.getId());

        return getReturningFlowRateById(returningFlowRateRequestDTO);
    }

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRateByTargetPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        FlowNodeRegisterMsg target = getFlowNodeRegisterMsgByPubkey(returningFlowRateRequestDTO.getTarget(), "目标");

        returningFlowRateRequestDTO.setTargetId(target.getId());

        return getReturningFlowRateByTargetId(returningFlowRateRequestDTO);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByMountedTransaction(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("挂载交易ID不能为空");
        }

        TransactionMountMsg transactionMountMsg = transactionMountMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("挂载交易ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findDistinctByRelatedTransactionMount(transactionMountMsg, pageable);
        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByStart(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("起点ID不能为空");
        }

        FlowNodeRegisterMsg startNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("起点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByStart(startNode, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByStartAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("起点ID不能为空");
        }

        FlowNodeRegisterMsg startNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("起点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByStartAndIsLoop(startNode, isLoop, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByEnd(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("终点ID不能为空");
        }

        FlowNodeRegisterMsg endNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("终点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByEnd(endNode, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByEndAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("终点ID不能为空");
        }

        FlowNodeRegisterMsg endNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("终点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByEndAndIsLoop(endNode, isLoop, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByNode(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("节点ID不能为空");
        }

        FlowNodeRegisterMsg node = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("节点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findDistinctByNode(node, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByNodeAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("节点ID不能为空");
        }

        FlowNodeRegisterMsg node = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("节点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findDistinctByNodeAndIsLoop(node, isLoop, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByRelatedId(
            UUID start,
            UUID end,
            UUID node,
            Boolean isLoop,
            Pageable pageable
    ) {
        if (countProvided(start, end, node) != 1) {
            throw new IllegalArgumentException("必须且只能提供start、end、node中的一个");
        }

        if (start != null) {
            return isLoop == null
                    ? getConsumeChainByStart(start, pageable)
                    : getConsumeChainByStartAndIsLoop(start, isLoop, pageable);
        }

        if (end != null) {
            return isLoop == null
                    ? getConsumeChainByEnd(end, pageable)
                    : getConsumeChainByEndAndIsLoop(end, isLoop, pageable);
        }

        return isLoop == null
                ? getConsumeChainByNode(node, pageable)
                : getConsumeChainByNodeAndIsLoop(node, isLoop, pageable);
    }

    @Override
    public Slice<ConsumeChainResponseDTO> getConsumeChainByPubkey(
            byte[] startPubkey,
            byte[] endPubkey,
            byte[] nodePubkey,
            Boolean isLoop,
            Pageable pageable
    ) {
        if (countProvided(startPubkey, endPubkey, nodePubkey) != 1) {
            throw new IllegalArgumentException("必须且只能提供startPubkey、endPubkey、nodePubkey中的一个");
        }

        if (startPubkey != null) {
            FlowNodeRegisterMsg start = getFlowNodeRegisterMsgByPubkey(startPubkey, "起点");
            Slice<ConsumeChain> consumeChains = isLoop == null
                    ? consumeChainRepository.findByStart(start, pageable)
                    : consumeChainRepository.findByStartAndIsLoop(start, isLoop, pageable);
            return getConsumeChainResponseDTOSlice(consumeChains);
        }

        if (endPubkey != null) {
            FlowNodeRegisterMsg end = getFlowNodeRegisterMsgByPubkey(endPubkey, "终点");
            Slice<ConsumeChain> consumeChains = isLoop == null
                    ? consumeChainRepository.findByEnd(end, pageable)
                    : consumeChainRepository.findByEndAndIsLoop(end, isLoop, pageable);
            return getConsumeChainResponseDTOSlice(consumeChains);
        }

        FlowNodeRegisterMsg node = getFlowNodeRegisterMsgByPubkey(nodePubkey, "节点");
        Slice<ConsumeChain> consumeChains = isLoop == null
                ? consumeChainRepository.findDistinctByNode(node, pageable)
                : consumeChainRepository.findDistinctByNodeAndIsLoop(node, isLoop, pageable);
        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    @Override
    public ConsumeChainResponseDTO getConsumeChainById(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("消费链ID不能为空");
        }

        ConsumeChain consumeChain = consumeChainRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("消费链ID不存在"));

        List<ConsumeChain> consumeChains = List.of(consumeChain);

        List<ConsumeChainResponseDTO> consumeChainResponseDTOs = getConsumeChainResponseDTOs(consumeChains);
        if (consumeChainResponseDTOs.isEmpty()) {
            throw new IllegalArgumentException("消费链ID不存在");
        }

        return consumeChainResponseDTOs.get(0);
    }

    private FlowNodeRegisterMsg getFlowNodeRegisterMsgByPubkey(byte[] flowNodePubkey, String roleName) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException(roleName + "流转节点公钥不能为空或长度不为33字节");
        }

        FlowNodeRegisterMsg flowNodeRegisterMsg = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(flowNodePubkey);
        if (flowNodeRegisterMsg == null) {
            throw new IllegalArgumentException(roleName + "流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")不存在");
        }

        return flowNodeRegisterMsg;
    }

    private int countProvided(Object... values) {
        int count = 0;
        for (Object value : values) {
            if (value != null) {
                count++;
            }
        }
        return count;
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
        Map<UUID, List<ConsumeChainEdge>> edgesByChainId = getEdgesByChainId(mountChains);
        List<ConsumeChainAllocationCandidate> candidates = new ArrayList<>();

        for (ConsumeChain mountChain : mountChains) {
            candidates.add(new ConsumeChainAllocationCandidate(
                    mountChain,
                    edgesByChainId.getOrDefault(mountChain.getId(), List.of())
            ));
        }

        return candidates;
    }

    /**
     * 获取消费链响应DTO列表
     *
     * @param consumeChains 消费链列表
     * @return 返回消费链响应DTO列表
     */
    private List<ConsumeChainResponseDTO> getConsumeChainResponseDTOs(List<ConsumeChain> consumeChains) {
        Map<UUID, List<ConsumeChainEdge>> edgesByChainId = getEdgesByChainId(consumeChains);
        List<ConsumeChainResponseDTO> consumeChainResponseDTOs = new ArrayList<>();

        for (ConsumeChain consumeChain : consumeChains) {
            ConsumeChainResponseDTO consumeChainResponseDTO = new ConsumeChainResponseDTO();

            consumeChainResponseDTO.setConsumeChain(consumeChain);
            consumeChainResponseDTO.setConsumeChainEdges(edgesByChainId.getOrDefault(consumeChain.getId(), List.of()));

            consumeChainResponseDTOs.add(consumeChainResponseDTO);
        }

        return consumeChainResponseDTOs;
    }

    private Slice<ConsumeChainResponseDTO> getConsumeChainResponseDTOSlice(Slice<ConsumeChain> consumeChains) {
        List<ConsumeChainResponseDTO> responseDTOs = getConsumeChainResponseDTOs(consumeChains.getContent());
        return new SliceImpl<>(responseDTOs, consumeChains.getPageable(), consumeChains.hasNext());
    }

    private Map<UUID, List<ConsumeChainEdge>> getEdgesByChainId(List<ConsumeChain> consumeChains) {
        Map<UUID, List<ConsumeChainEdge>> edgesByChainId = new HashMap<>();
        if (consumeChains.isEmpty()) {
            return edgesByChainId;
        }

        List<ConsumeChainEdge> consumeChainEdges = consumeChainEdgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(consumeChains);
        for (ConsumeChainEdge consumeChainEdge : consumeChainEdges) {
            UUID chainId = consumeChainEdge.getChain().getId();
            edgesByChainId.computeIfAbsent(chainId, unused -> new ArrayList<>()).add(consumeChainEdge);
        }

        return edgesByChainId;
    }

    private double amount(BigDecimal amount) {
        return amount == null ? 0.0 : amount.doubleValue();
    }

}
