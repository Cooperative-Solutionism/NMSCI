package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

public interface TransactionMountMsgService {

    /**
     * 保存交易挂载消息
     *
     * @param transactionMountMsg 交易挂载消息
     * @return 保存后的交易挂载消息
     */
    TransactionMountMsg saveTransactionMountMsg(@Valid @Nonnull TransactionMountMsg transactionMountMsg);

    /**
     * 根据ID获取交易挂载消息
     *
     * @param id 交易挂载消息ID
     * @return 交易挂载消息
     */
    TransactionMountMsg getTransactionMountMsgById(UUID id);

    /**
     * 根据挂载的交易记录ID获取交易挂载消息
     *
     * @param id 挂载的交易记录ID
     * @return 交易挂载消息
     */
    TransactionMountMsg getTransactionMountMsgByMountedTransactionRecordId(UUID id);

    /**
     * 根据消费节点公钥获取交易挂载消息
     *
     * @param consumeNodePubkey 消费节点公钥
     * @return 交易挂载消息列表
     */
    Slice<TransactionMountMsg> getTransactionMountMsgByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable);

    /**
     * 根据流转节点公钥获取交易挂载消息
     *
     * @param flowNodePubkey 流程节点公钥
     * @return 交易挂载消息列表
     */
    Slice<TransactionMountMsg> getTransactionMountMsgByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    /**
     * 根据消费节点公钥和流转节点公钥获取交易挂载消息
     *
     * @param consumeNodePubkey 消费节点公钥
     * @param flowNodePubkey 流转节点公钥
     * @return 交易挂载消息列表
     */
    Slice<TransactionMountMsg> getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey, Pageable pageable);

}
