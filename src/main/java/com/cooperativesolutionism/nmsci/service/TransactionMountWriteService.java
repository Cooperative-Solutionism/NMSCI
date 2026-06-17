package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class TransactionMountWriteService {

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private MessageWritePipeline messageWritePipeline;

    @Resource
    private ConsumeChainAllocationService consumeChainAllocationService;

    @Transactional
    public TransactionMountMsg saveAndAllocate(TransactionMountMsg transactionMountMsg) {
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgRepository.findByIdForUpdate(
                transactionMountMsg.getMountedTransactionRecordId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")不存在"
        ));

        if (transactionMountMsgRepository.existsTransactionMountMsgByMountedTransactionRecordId(
                transactionMountMsg.getMountedTransactionRecordId())) {
            throw new ConflictException("挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")已被挂载");
        }

        byte[] consumeNodePubkey = transactionRecordMsg.getConsumeNodePubkey();
        if (!Arrays.equals(transactionMountMsg.getConsumeNodePubkey(), consumeNodePubkey)) {
            String consumeNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(consumeNodePubkey);
            throw new IllegalArgumentException("挂载的交易记录信息中的消费节点公钥(" + consumeNodePubkeyBase64 + ")与当前交易挂载信息中的消费节点公钥不一致");
        }

        TransactionMountMsg transactionMountMsgInDb = messageWritePipeline.saveEntityThenAbstract(
                transactionMountMsg,
                transactionMountMsgRepository::save
        );
        consumeChainAllocationService.saveConsumeChain(transactionMountMsgInDb, transactionRecordMsg);
        return transactionMountMsgInDb;
    }
}
