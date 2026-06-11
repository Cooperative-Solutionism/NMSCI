package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

public interface TransactionRecordMsgService {

    /**
     * 保存交易记录消息
     *
     * @param transactionRecordMsg 交易记录消息对象
     * @return 保存后的交易记录消息对象
     */
    TransactionRecordMsg saveTransactionRecordMsg(@Valid @Nonnull TransactionRecordMsg transactionRecordMsg);

    /**
     * 根据ID获取交易记录消息
     *
     * @param id 交易记录消息ID
     * @return 交易记录消息对象
     */
    TransactionRecordMsg getTransactionRecordMsgById(UUID id);

    /**
     * 根据消费节点公钥获取交易记录消息列表
     *
     * @param consumeNodePubkey 消费节点公钥
     * @return 交易记录消息列表
     */
    Slice<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable);

    /**
     * 根据流转节点公钥获取交易记录消息列表
     *
     * @param flowNodePubkey 流转节点公钥
     * @return 交易记录消息列表
     */
    Slice<TransactionRecordMsg> getTransactionRecordMsgByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    /**
     * 根据消费节点公钥和流转节点公钥获取交易记录消息列表
     *
     * @param consumeNodePubkey 消费节点公钥
     * @param flowNodePubkey 流转节点公钥
     * @return 交易记录消息列表
     */
    Slice<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey, Pageable pageable);
}
