package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.consume.ConsumeChainSupport;
import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsumeChainQueryService {

    @Resource
    private ConsumeChainSupport consumeChainSupport;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainEdgeRepository consumeChainEdgeRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    public ReturningFlowRateResponseDTO getReturningFlowRateById(ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        if (!CurrencyTypeEnum.containsValue(returningFlowRateRequestDTO.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值：\n" + CurrencyTypeEnum.getAllEnumDescriptions());
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

    public ReturningFlowRateResponseDTO getReturningFlowRateByTargetId(ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        if (!CurrencyTypeEnum.containsValue(returningFlowRateRequestDTO.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值：\n" + CurrencyTypeEnum.getAllEnumDescriptions());
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

    public ReturningFlowRateResponseDTO getReturningFlowRateByPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        FlowNodeRegisterMsg source = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(returningFlowRateRequestDTO.getSource(), "源");
        FlowNodeRegisterMsg target = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(returningFlowRateRequestDTO.getTarget(), "目标");

        returningFlowRateRequestDTO.setTargetId(target.getId());
        returningFlowRateRequestDTO.setSourceId(source.getId());

        return getReturningFlowRateById(returningFlowRateRequestDTO);
    }

    public ReturningFlowRateResponseDTO getReturningFlowRateByTargetPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        FlowNodeRegisterMsg target = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(returningFlowRateRequestDTO.getTarget(), "目标");

        returningFlowRateRequestDTO.setTargetId(target.getId());

        return getReturningFlowRateByTargetId(returningFlowRateRequestDTO);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByMountedTransaction(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("挂载交易ID不能为空");
        }

        TransactionMountMsg transactionMountMsg = transactionMountMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("挂载交易ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findDistinctByRelatedTransactionMount(transactionMountMsg, pageable);
        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByStart(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("起点ID不能为空");
        }

        FlowNodeRegisterMsg startNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("起点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByStart(startNode, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByStartAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("起点ID不能为空");
        }

        FlowNodeRegisterMsg startNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("起点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByStartAndIsLoop(startNode, isLoop, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByEnd(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("终点ID不能为空");
        }

        FlowNodeRegisterMsg endNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("终点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByEnd(endNode, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByEndAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("终点ID不能为空");
        }

        FlowNodeRegisterMsg endNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("终点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findByEndAndIsLoop(endNode, isLoop, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByNode(UUID id, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("节点ID不能为空");
        }

        FlowNodeRegisterMsg node = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("节点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findDistinctByNode(node, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public Slice<ConsumeChainResponseDTO> getConsumeChainByNodeAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
        if (id == null) {
            throw new IllegalArgumentException("节点ID不能为空");
        }

        FlowNodeRegisterMsg node = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("节点ID不存在"));

        Slice<ConsumeChain> consumeChains = consumeChainRepository.findDistinctByNodeAndIsLoop(node, isLoop, pageable);

        return getConsumeChainResponseDTOSlice(consumeChains);
    }

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
            FlowNodeRegisterMsg start = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(startPubkey, "起点");
            Slice<ConsumeChain> consumeChains = isLoop == null
                    ? consumeChainRepository.findByStart(start, pageable)
                    : consumeChainRepository.findByStartAndIsLoop(start, isLoop, pageable);
            return getConsumeChainResponseDTOSlice(consumeChains);
        }

        if (endPubkey != null) {
            FlowNodeRegisterMsg end = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(endPubkey, "终点");
            Slice<ConsumeChain> consumeChains = isLoop == null
                    ? consumeChainRepository.findByEnd(end, pageable)
                    : consumeChainRepository.findByEndAndIsLoop(end, isLoop, pageable);
            return getConsumeChainResponseDTOSlice(consumeChains);
        }

        FlowNodeRegisterMsg node = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(nodePubkey, "节点");
        Slice<ConsumeChain> consumeChains = isLoop == null
                ? consumeChainRepository.findDistinctByNode(node, pageable)
                : consumeChainRepository.findDistinctByNodeAndIsLoop(node, isLoop, pageable);
        return getConsumeChainResponseDTOSlice(consumeChains);
    }

    public ConsumeChainResponseDTO getConsumeChainById(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("消费链ID不能为空");
        }

        ConsumeChain consumeChain = consumeChainRepository.findById(uuid)
                .orElseThrow(() -> new NotFoundException("消费链ID不存在"));

        List<ConsumeChain> consumeChains = List.of(consumeChain);

        List<ConsumeChainResponseDTO> consumeChainResponseDTOs = getConsumeChainResponseDTOs(consumeChains);
        if (consumeChainResponseDTOs.isEmpty()) {
            throw new NotFoundException("消费链ID不存在");
        }

        return consumeChainResponseDTOs.get(0);
    }

    /**
     * 按 id 查询消费链边：source 缺省时返回流入 target 的全部边（按 chain 去重）；
     * source、target 均提供时返回 source→target 之间的边。
     */
    public List<ConsumeChainEdge> getConsumeChainEdgesById(UUID sourceId, UUID targetId, short currencyType, long startTime, long endTime) {
        if (!CurrencyTypeEnum.containsValue(currencyType)) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值：\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId 不能为空");
        }

        if (sourceId == null) {
            return consumeChainEdgeRepository.findConsumeChainEdgesByTarget(targetId, currencyType, startTime, endTime);
        }
        return consumeChainEdgeRepository.findConsumeChainEdges(sourceId, targetId, currencyType, startTime, endTime);
    }

    /**
     * 按 pubkey 查询消费链边：先把 pubkey 解析为流转节点 id，再委托 {@link #getConsumeChainEdgesById}。
     */
    public List<ConsumeChainEdge> getConsumeChainEdgesByPubkey(byte[] sourcePubkey, byte[] targetPubkey, short currencyType, long startTime, long endTime) {
        FlowNodeRegisterMsg target = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(targetPubkey, "目标");

        if (sourcePubkey == null) {
            return getConsumeChainEdgesById(null, target.getId(), currencyType, startTime, endTime);
        }

        FlowNodeRegisterMsg source = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(sourcePubkey, "源");
        return getConsumeChainEdgesById(source.getId(), target.getId(), currencyType, startTime, endTime);
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

    /**
     * 获取消费链响应DTO列表
     *
     * @param consumeChains 消费链列表
     * @return 返回消费链响应DTO列表
     */
    private List<ConsumeChainResponseDTO> getConsumeChainResponseDTOs(List<ConsumeChain> consumeChains) {
        Map<UUID, List<ConsumeChainEdge>> edgesByChainId = consumeChainSupport.getEdgesByChainId(consumeChains);
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

    private double amount(BigDecimal amount) {
        return amount == null ? 0.0 : amount.doubleValue();
    }

}
