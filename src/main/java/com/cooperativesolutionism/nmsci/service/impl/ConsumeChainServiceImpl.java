package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.model.*;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConsumeChainServiceImpl implements ConsumeChainService {

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainEdgeRepository consumeChainEdgeRepository;

    @Override
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

                    restAmount -= mountChainAmount;
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
            }
        }

    }

    @Override
    public ReturningFlowRateResponseDTO getReturningFlowRate(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO) {
        if (!CurrencyTypeEnum.containsValue(returningFlowRateRequestDTO.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        FlowNodeRegisterMsg source = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(returningFlowRateRequestDTO.getSource());
        FlowNodeRegisterMsg target = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(returningFlowRateRequestDTO.getTarget());

        List<ConsumeChainEdge> consumeChainEdges = consumeChainEdgeRepository.findConsumeChainEdges(
                source.getId(),
                target.getId(),
                returningFlowRateRequestDTO.getCurrencyType(),
                returningFlowRateRequestDTO.getStartTime(),
                returningFlowRateRequestDTO.getEndTime()
        );

        List<ConsumeChainEdge> consumeChainEdgesByOnlyTarget = consumeChainEdgeRepository.findConsumeChainEdgesByTarget(
                target.getId(),
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

        double targetTotalUnloopedAmount = 0.0;
        for (ConsumeChainEdge edge : consumeChainEdgesByOnlyTarget) {
            if (!edge.getIsLoop()) {
                targetTotalUnloopedAmount += edge.getAmount();
            }
        }

        double returningFlowRate = (loopedAmount + unloopedAmount) == 0 ? 100.0 : loopedAmount / (loopedAmount + unloopedAmount) * 100.0;

        return new ReturningFlowRateResponseDTO(
                returningFlowRate,
                loopedAmount,
                unloopedAmount,
                targetTotalUnloopedAmount,
                returningFlowRateRequestDTO.getCurrencyType()
        );
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

        System.out.println("ConsumeChain = " + consumeChain);
        for (ConsumeChainEdge consumeChainEdge : consumeChainEdges) {
            consumeChainEdge.setIsLoop(isLoop);
        }
        System.out.println("consumeChainEdges = " + consumeChainEdges);

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

}
