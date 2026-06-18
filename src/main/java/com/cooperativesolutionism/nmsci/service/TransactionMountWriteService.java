package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class TransactionMountWriteService {

    private final TransactionRecordMsgRepository transactionRecordMsgRepository;
    private final TransactionMountMsgRepository transactionMountMsgRepository;
    private final MessageWritePipeline messageWritePipeline;
    private final ConsumeChainAllocationService consumeChainAllocationService;
    private final FlowNodeStateValidator flowNodeStateValidator;
    private final CentralPubkeyValidator centralPubkeyValidator;

    public TransactionMountWriteService(
            TransactionRecordMsgRepository transactionRecordMsgRepository,
            TransactionMountMsgRepository transactionMountMsgRepository,
            MessageWritePipeline messageWritePipeline,
            ConsumeChainAllocationService consumeChainAllocationService,
            FlowNodeStateValidator flowNodeStateValidator,
            CentralPubkeyValidator centralPubkeyValidator
    ) {
        this.transactionRecordMsgRepository = transactionRecordMsgRepository;
        this.transactionMountMsgRepository = transactionMountMsgRepository;
        this.messageWritePipeline = messageWritePipeline;
        this.consumeChainAllocationService = consumeChainAllocationService;
        this.flowNodeStateValidator = flowNodeStateValidator;
        this.centralPubkeyValidator = centralPubkeyValidator;
    }

    @Transactional
    public TransactionMountMsg saveAndAllocate(TransactionMountMsg transactionMountMsg) {
        // 二次校验（TOCTOU 收口，见 review #4）：流转节点的注册/授权/冻结与中心公钥冻结状态，
        // 在 TransactionMountMsgService 的无事务前置校验之后、本分配事务提交之前仍可能被并发的
        // 锁定/授权消息改变。这里在分配事务内重新核验，把竞态窗口收敛到事务内（READ COMMITTED 下
        // 可见任意已提交的状态变更；仅与本事务严格并发、尚未提交的锁定属可接受的串行化二选一）。
        flowNodeStateValidator.validateRegisteredAuthorizedAndNotLocked(
                transactionMountMsg.getFlowNodePubkey(),
                centralPubkeyValidator.currentCentralPubkey()
        );
        centralPubkeyValidator.validateNotLocked(transactionMountMsg.getCentralPubkey());

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
