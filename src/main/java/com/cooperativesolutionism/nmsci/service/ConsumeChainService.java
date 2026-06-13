package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.annotation.Nonnull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

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
    Slice<ConsumeChainResponseDTO> getConsumeChainByMountedTransaction(UUID id, Pageable pageable);

    /**
     * 根据起点ID获取消费链数据
     *
     * @param id 起点ID
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByStart(UUID id, Pageable pageable);

    /**
     * 根据起点ID和是否成环获取消费链数据
     *
     * @param id   起点ID
     * @param isLoop 是否成环
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByStartAndIsLoop(UUID id, Boolean isLoop, Pageable pageable);

    /**
     * 根据终点ID获取消费链数据
     *
     * @param id   终点ID
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByEnd(UUID id, Pageable pageable);

    /**
     * 根据终点ID和是否成环获取消费链数据
     *
     * @param id   终点ID
     * @param isLoop 是否成环
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByEndAndIsLoop(UUID id, Boolean isLoop, Pageable pageable);

    /**
     * 根据节点ID获取包含该节点的消费链数据
     *
     * @param id 节点ID
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByNode(UUID id, Pageable pageable);

    /**
     * 根据节点ID和是否成环获取包含该节点的消费链数据
     *
     * @param id 节点ID
     * @param isLoop 是否成环
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByNodeAndIsLoop(UUID id, Boolean isLoop, Pageable pageable);

    /**
     * 根据起点、终点或任意节点ID分页查询消费链数据
     *
     * @param start   起点流转节点注册消息ID
     * @param end     终点流转节点注册消息ID
     * @param node    任意节点流转节点注册消息ID
     * @param isLoop  是否成环；null时不过滤
     * @param pageable 分页参数
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByRelatedId(
            UUID start,
            UUID end,
            UUID node,
            Boolean isLoop,
            Pageable pageable
    );

    /**
     * 根据起点、终点或任意节点公钥分页查询消费链数据
     *
     * @param startPubkey 起点流转节点公钥
     * @param endPubkey   终点流转节点公钥
     * @param nodePubkey  任意节点公钥
     * @param isLoop      是否成环；null时不过滤
     * @param pageable    分页参数
     * @return 返回消费链数据
     */
    Slice<ConsumeChainResponseDTO> getConsumeChainByPubkey(
            byte[] startPubkey,
            byte[] endPubkey,
            byte[] nodePubkey,
            Boolean isLoop,
            Pageable pageable
    );

    ConsumeChainResponseDTO getConsumeChainById(UUID uuid);
}
