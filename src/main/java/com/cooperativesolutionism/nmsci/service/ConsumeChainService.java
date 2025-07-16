package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.UUID;

public interface ConsumeChainService {

    /**
     * 保存消费链数据
     *
     * @param transactionMountMsg  消费链挂载消息
     * @param transactionRecordMsg 消费链记录消息
     */
    void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg);

    /**
     * 通过ID获取回流率
     *
     * @param returningFlowRateRequestDTO 请求参数
     * @return 返回回流率
     */
    ReturningFlowRateResponseDTO getReturningFlowRateById(ReturningFlowRateRequestDTO returningFlowRateRequestDTO);

    /**
     * 通过目标ID获取回流率
     *
     * @param returningFlowRateRequestDTO 请求参数
     * @return 返回回流率
     */
    ReturningFlowRateResponseDTO getReturningFlowRateByTargetId(ReturningFlowRateRequestDTO returningFlowRateRequestDTO);

    /**
     * 通过公钥获取回流率
     *
     * @param returningFlowRateRequestDTO 请求参数
     * @return 返回回流率
     */
    ReturningFlowRateResponseDTO getReturningFlowRateByPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO);

    /**
     * 通过目标公钥获取回流率
     *
     * @param returningFlowRateRequestDTO 请求参数
     * @return 返回回流率
     */
    ReturningFlowRateResponseDTO getReturningFlowRateByTargetPubkey(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO);

    /**
     * 根据挂载交易ID获取消费链数据
     *
     * @param id 消费链挂载交易ID
     * @return 返回消费链数据
     */
    List<ConsumeChainResponseDTO> getConsumeChainByMountedTransaction(UUID id);

    /**
     * 根据起点ID获取消费链数据
     *
     * @param id 起点ID
     * @return 返回消费链数据
     */
    List<ConsumeChainResponseDTO> getConsumeChainByStart(UUID id);

    /**
     * 根据起点ID和是否成环获取消费链数据
     *
     * @param id   起点ID
     * @param isLoop 是否成环
     * @return 返回消费链数据
     */
    List<ConsumeChainResponseDTO> getConsumeChainByStartAndIsLoop(UUID id, Boolean isLoop);

    /**
     * 根据终点ID获取消费链数据
     *
     * @param id   终点ID
     * @return 返回消费链数据
     */
    List<ConsumeChainResponseDTO> getConsumeChainByEnd(UUID id);

    /**
     * 根据终点ID和是否成环获取消费链数据
     *
     * @param id   终点ID
     * @param isLoop 是否成环
     * @return 返回消费链数据
     */
    List<ConsumeChainResponseDTO> getConsumeChainByEndAndIsLoop(UUID id, Boolean isLoop);
}
