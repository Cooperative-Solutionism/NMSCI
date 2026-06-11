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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ConsumeChainServiceImpl implements ConsumeChainService {

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

        List<ConsumeChain> mountChains = consumeChainRepository.findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(
                source,
                transactionRecordMsg.getCurrencyType()
        );

        List<ConsumeChainAllocationCandidate> candidates = new ArrayList<>();
        for (ConsumeChain mountChain : mountChains) {
            candidates.add(new ConsumeChainAllocationCandidate(
                    mountChain,
                    consumeChainEdgeRepository.findByChain(mountChain)
            ));
        }

        ConsumeChainAllocationPlan plan = consumeChainAllocator.allocate(
                transactionMountMsg,
                transactionRecordMsg,
                source,
                target,
                candidates
        );
        consumeChainPersistenceService.save(plan);
    }

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRateById(ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        if (!CurrencyTypeEnum.containsValue(returningFlowRateRequestDTO.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        List<ConsumeChainEdge> consumeChainEdges = consumeChainEdgeRepository.findConsumeChainEdges(
                returningFlowRateRequestDTO.getSourceId(),
                returningFlowRateRequestDTO.getTargetId(),
                returningFlowRateRequestDTO.getCurrencyType(),
                returningFlowRateRequestDTO.getStartTime(),
                returningFlowRateRequestDTO.getEndTime()
        );

        double loopedAmount = 0.0;
        double unloopedAmount = 0.0;

        for (ConsumeChainEdge edge : consumeChainEdges) {
            if (edge.getIsLoop()) {
                loopedAmount += edge.getAmount();
            } else {
                unloopedAmount += edge.getAmount();
            }
        }

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

        List<ConsumeChainEdge> consumeChainEdgesByOnlyTarget = consumeChainEdgeRepository.findConsumeChainEdgesByTarget(
                returningFlowRateRequestDTO.getTargetId(),
                returningFlowRateRequestDTO.getCurrencyType(),
                returningFlowRateRequestDTO.getStartTime(),
                returningFlowRateRequestDTO.getEndTime()
        );

        double targetTotalLoopedAmount = 0.0;
        double targetTotalUnloopedAmount = 0.0;
        for (ConsumeChainEdge edge : consumeChainEdgesByOnlyTarget) {
            if (edge.getIsLoop()) {
                targetTotalLoopedAmount += edge.getAmount();
            } else {
                targetTotalUnloopedAmount += edge.getAmount();
            }
        }

        return new ReturningFlowRateResponseDTO(
                targetTotalLoopedAmount,
                targetTotalUnloopedAmount,
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
    public List<ConsumeChainResponseDTO> getConsumeChainByMountedTransaction(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("挂载交易ID不能为空");
        }

        TransactionMountMsg transactionMountMsg = transactionMountMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("挂载交易ID不存在"));

        List<ConsumeChainEdge> consumeChainEdges = consumeChainEdgeRepository.findByRelatedTransactionMount(transactionMountMsg);

        List<ConsumeChain> consumeChains = new ArrayList<>();
        for (ConsumeChainEdge edge : consumeChainEdges) {
            ConsumeChain consumeChain = consumeChainRepository.findById(edge.getChain().getId())
                    .orElseThrow(() -> new IllegalArgumentException("消费链条不存在"));

            consumeChains.add(consumeChain);
        }

        return getConsumeChainResponseDTOs(consumeChains);
    }

    @Override
    public List<ConsumeChainResponseDTO> getConsumeChainByStart(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("起点ID不能为空");
        }

        FlowNodeRegisterMsg startNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("起点ID不存在"));

        List<ConsumeChain> consumeChains = consumeChainRepository.findByStart(startNode);

        return getConsumeChainResponseDTOs(consumeChains);
    }

    @Override
    public List<ConsumeChainResponseDTO> getConsumeChainByStartAndIsLoop(UUID id, Boolean isLoop) {
        if (id == null) {
            throw new IllegalArgumentException("起点ID不能为空");
        }

        FlowNodeRegisterMsg startNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("起点ID不存在"));

        List<ConsumeChain> consumeChains = consumeChainRepository.findByStartAndIsLoop(startNode, isLoop);

        return getConsumeChainResponseDTOs(consumeChains);
    }

    @Override
    public List<ConsumeChainResponseDTO> getConsumeChainByEnd(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("终点ID不能为空");
        }

        FlowNodeRegisterMsg endNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("终点ID不存在"));

        List<ConsumeChain> consumeChains = consumeChainRepository.findByEnd(endNode);

        return getConsumeChainResponseDTOs(consumeChains);
    }

    @Override
    public List<ConsumeChainResponseDTO> getConsumeChainByEndAndIsLoop(UUID id, Boolean isLoop) {
        if (id == null) {
            throw new IllegalArgumentException("终点ID不能为空");
        }

        FlowNodeRegisterMsg endNode = flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("终点ID不存在"));

        List<ConsumeChain> consumeChains = consumeChainRepository.findByEndAndIsLoop(endNode, isLoop);

        return getConsumeChainResponseDTOs(consumeChains);
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

    /**
     * 获取消费链响应DTO列表
     *
     * @param consumeChains 消费链列表
     * @return 返回消费链响应DTO列表
     */
    private List<ConsumeChainResponseDTO> getConsumeChainResponseDTOs(List<ConsumeChain> consumeChains) {
        List<ConsumeChainResponseDTO> consumeChainResponseDTOs = new ArrayList<>();

        for (ConsumeChain consumeChain : consumeChains) {
            ConsumeChainResponseDTO consumeChainResponseDTO = new ConsumeChainResponseDTO();
            List<ConsumeChainEdge> consumeChainEdges = consumeChainEdgeRepository.findByChainOrderByRelatedTransactionMountTimestampAsc(consumeChain);

            consumeChainResponseDTO.setConsumeChain(consumeChain);
            consumeChainResponseDTO.setConsumeChainEdges(consumeChainEdges);

            consumeChainResponseDTOs.add(consumeChainResponseDTO);
        }

        return consumeChainResponseDTOs;
    }

}
