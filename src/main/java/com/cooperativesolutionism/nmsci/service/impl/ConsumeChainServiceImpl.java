package com.cooperativesolutionism.nmsci.service.impl;

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

    @Override
    @Transactional
    public void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg) {
        FlowNodeRegisterMsg source = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(transactionMountMsg.getFlowNodePubkey());
        FlowNodeRegisterMsg target = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(transactionRecordMsg.getFlowNodePubkey());

        List<ConsumeChain> mountChains = consumeChainRepository.findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(
                source,
                transactionRecordMsg.getCurrencyType()
        );

        // 如果没有已有的挂载链条，则直接创建新的消费链条
        if (mountChains.isEmpty()) {
            ConsumeChain consumeChain = new ConsumeChain();
            consumeChain.setStart(source);
            consumeChain.setEnd(target);
            consumeChain.setAmount(transactionRecordMsg.getAmount());
            consumeChain.setCurrencyType(transactionRecordMsg.getCurrencyType());
            consumeChain.setTailMountTimestamp(transactionMountMsg.getConfirmTimestamp());
            ConsumeChain consumeChainInDb = saveConsumeChainWithTestLoop(consumeChain);

            ConsumeChainEdge consumeChainEdge = new ConsumeChainEdge();
            consumeChainEdge.setSource(source);
            consumeChainEdge.setTarget(target);
            consumeChainEdge.setAmount(transactionRecordMsg.getAmount());
            consumeChainEdge.setCurrencyType(transactionRecordMsg.getCurrencyType());
            consumeChainEdge.setChain(consumeChainInDb);
            consumeChainEdge.setRelatedTransactionRecord(transactionRecordMsg);
            consumeChainEdge.setRelatedTransactionMount(transactionMountMsg);
            consumeChainEdge.setRelatedTransactionMountTimestamp(transactionMountMsg.getConfirmTimestamp());
            saveAllConsumeChainEdgesWithTestLoop(consumeChainEdge);
        }

        // 如果有挂载链条，则需要处理挂载链条
        if (!mountChains.isEmpty()) {
            long restAmount = transactionRecordMsg.getAmount();
            for (ConsumeChain mountChain : mountChains) {
                long mountChainAmount = mountChain.getAmount();

                // 如果剩余金额小于等于0，则不需要继续处理
                if (restAmount <= 0) break;

                // 如果剩余金额大于等于该挂载链条金额，则直接延伸链条
                if (restAmount >= mountChainAmount) {
                    mountChain.setEnd(target);
                    mountChain.setTailMountTimestamp(transactionMountMsg.getConfirmTimestamp());
                    ConsumeChain mountChainInDb = saveConsumeChainWithTestLoop(mountChain);

                    ConsumeChainEdge consumeChainEdge = new ConsumeChainEdge();
                    consumeChainEdge.setSource(source);
                    consumeChainEdge.setTarget(target);
                    consumeChainEdge.setAmount(mountChainAmount);
                    consumeChainEdge.setCurrencyType(transactionRecordMsg.getCurrencyType());
                    consumeChainEdge.setChain(mountChainInDb);
                    consumeChainEdge.setRelatedTransactionRecord(transactionRecordMsg);
                    consumeChainEdge.setRelatedTransactionMount(transactionMountMsg);
                    consumeChainEdge.setRelatedTransactionMountTimestamp(transactionMountMsg.getConfirmTimestamp());
                    saveAllConsumeChainEdgesWithTestLoop(consumeChainEdge);
                }

                // 如果剩余金额小于该挂载链条金额，则需要分裂链条
                if (restAmount < mountChainAmount) {
                    ConsumeChain newConsumeChain = new ConsumeChain();
                    newConsumeChain.setStart(mountChain.getStart());
                    newConsumeChain.setEnd(target);
                    newConsumeChain.setAmount(restAmount);
                    newConsumeChain.setCurrencyType(transactionRecordMsg.getCurrencyType());
                    newConsumeChain.setTailMountTimestamp(transactionMountMsg.getConfirmTimestamp());
                    ConsumeChain newConsumeChainInDb = saveConsumeChainWithTestLoop(newConsumeChain);

                    ConsumeChainEdge newConsumeChainEdge = new ConsumeChainEdge();
                    newConsumeChainEdge.setSource(source);
                    newConsumeChainEdge.setTarget(target);
                    newConsumeChainEdge.setAmount(restAmount);
                    newConsumeChainEdge.setCurrencyType(transactionRecordMsg.getCurrencyType());
                    newConsumeChainEdge.setChain(newConsumeChainInDb);
                    newConsumeChainEdge.setRelatedTransactionRecord(transactionRecordMsg);
                    newConsumeChainEdge.setRelatedTransactionMount(transactionMountMsg);
                    newConsumeChainEdge.setRelatedTransactionMountTimestamp(transactionMountMsg.getConfirmTimestamp());
                    saveAllConsumeChainEdgesWithTestLoop(newConsumeChainEdge);

                    // 更新原链条金额
                    mountChain.setAmount(mountChainAmount - restAmount);
                    ConsumeChain mountChainInDb = saveConsumeChainWithTestLoop(mountChain);

                    // 更新原链条边的金额并复制一份作为新链条的边
                    List<ConsumeChainEdge> originEdges = consumeChainEdgeRepository.findByChain(mountChain);
                    List<ConsumeChainEdge> newEdges = new ArrayList<>();
                    for (ConsumeChainEdge originEdge : originEdges) {
                        // 更新原链条边的金额
                        originEdge.setAmount(mountChainInDb.getAmount());

                        ConsumeChainEdge newEdge = new ConsumeChainEdge();
                        newEdge.setSource(originEdge.getSource());
                        newEdge.setTarget(originEdge.getTarget());
                        newEdge.setAmount(restAmount);
                        newEdge.setCurrencyType(originEdge.getCurrencyType());
                        newEdge.setChain(newConsumeChainInDb);
                        newEdge.setRelatedTransactionRecord(originEdge.getRelatedTransactionRecord());
                        newEdge.setRelatedTransactionMount(originEdge.getRelatedTransactionMount());
                        newEdge.setRelatedTransactionMountTimestamp(originEdge.getRelatedTransactionMountTimestamp());
                        newEdges.add(newEdge);
                    }

                    saveAllConsumeChainEdgesWithTestLoop(originEdges);
                    saveAllConsumeChainEdgesWithTestLoop(newEdges);
                }

                restAmount -= mountChainAmount;
            }
        }

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

        double returningFlowRate = (loopedAmount + unloopedAmount) == 0 ? 100.0 : loopedAmount / (loopedAmount + unloopedAmount) * 100.0;

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
        FlowNodeRegisterMsg source = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(returningFlowRateRequestDTO.getSource());
        FlowNodeRegisterMsg target = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(returningFlowRateRequestDTO.getTarget());

        returningFlowRateRequestDTO.setTargetId(target.getId());
        returningFlowRateRequestDTO.setSourceId(source.getId());

        return getReturningFlowRateById(returningFlowRateRequestDTO);
    }

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRateByTargetPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        FlowNodeRegisterMsg target = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(returningFlowRateRequestDTO.getTarget());

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

    /**
     * 在保存消费链之前先检测消费链是否已成环
     *
     * @param consumeChain 消费链对象
     * @return 保存后的消费链对象
     */
    private ConsumeChain saveConsumeChainWithTestLoop(@Nonnull ConsumeChain consumeChain) {
        boolean isLoop = consumeChain.getStart().equals(consumeChain.getEnd());
        consumeChain.setIsLoop(isLoop);

        return consumeChainRepository.save(consumeChain);
    }

    /**
     * 在保存消费链边之前先检测所属消费链边是否已成环
     *
     * @param consumeChainEdges 消费链边对象列表
     */
    private void saveAllConsumeChainEdgesWithTestLoop(@Nonnull List<ConsumeChainEdge> consumeChainEdges) {
        if (consumeChainEdges.isEmpty()) {
            return;
        }

        ConsumeChain consumeChain = consumeChainEdges.get(0).getChain();
        boolean isLoop = consumeChain.getStart().equals(consumeChain.getEnd());

        for (ConsumeChainEdge consumeChainEdge : consumeChainEdges) {
            consumeChainEdge.setIsLoop(isLoop);
        }

        consumeChainEdgeRepository.saveAll(consumeChainEdges);
    }

    /**
     * 在保存消费链边之前先检测所属消费链边是否已成环
     *
     * @param consumeChainEdge 消费链边对象
     */
    private void saveAllConsumeChainEdgesWithTestLoop(@Nonnull ConsumeChainEdge consumeChainEdge) {
        saveAllConsumeChainEdgesWithTestLoop(
                List.of(consumeChainEdge)
        );
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
