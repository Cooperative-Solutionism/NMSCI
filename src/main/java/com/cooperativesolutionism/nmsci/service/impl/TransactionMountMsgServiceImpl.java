package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.protocol.BlockDifficultyService;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProofOfWorkValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Validated
public class TransactionMountMsgServiceImpl implements TransactionMountMsgService {
    @Resource
    private BlockDifficultyService blockDifficultyService;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Resource
    private ConsumeChainService consumeChainService;

    @Resource
    private FlowNodeStateValidator flowNodeStateValidator;

    @Resource
    private CentralPubkeyValidator centralPubkeyValidator;

    @Resource
    private SignatureValidator signatureValidator;

    @Resource
    private ProofOfWorkValidator proofOfWorkValidator;

    @Resource
    private ProtocolRawBytesBuilder protocolRawBytesBuilder;

    @Resource
    private CentralSignatureService centralSignatureService;

    @Override
    @Transactional
    public TransactionMountMsg saveTransactionMountMsg(@Valid @Nonnull TransactionMountMsg transactionMountMsg) {
        if (transactionMountMsg.getMsgType() != MsgTypeEnum.TransactionMountMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.TransactionMountMsg.getValue());
        }

        if (transactionMountMsgRepository.existsById(transactionMountMsg.getId())) {
            throw new IllegalArgumentException("该交易挂载信息id(" + transactionMountMsg.getId() + ")已存在");
        }

        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgRepository.findByIdForUpdate(transactionMountMsg.getMountedTransactionRecordId())
                .orElseThrow(() -> new IllegalArgumentException("挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")不存在"));

        if (transactionMountMsgRepository.existsTransactionMountMsgByMountedTransactionRecordId(transactionMountMsg.getMountedTransactionRecordId())) {
            throw new IllegalArgumentException("挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")已被挂载");
        }

        int transactionDifficultyTargetNbits = blockDifficultyService.currentTransactionDifficultyTarget();
        if (!transactionMountMsg.getTransactionDifficultyTarget().equals(transactionDifficultyTargetNbits)) {
            throw new IllegalArgumentException("交易难度目标与前区块中的交易难度目标不一致");
        }

        // 消费节点公钥与要挂载的交易信息中的消费节点公钥需相同
        byte[] consumeNodePubkey = transactionRecordMsg.getConsumeNodePubkey();
        if (!Arrays.equals(transactionMountMsg.getConsumeNodePubkey(), consumeNodePubkey)) {
            String consumeNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(consumeNodePubkey);
            throw new IllegalArgumentException("挂载的交易记录信息中的消费节点公钥(" + consumeNodePubkeyBase64 + ")与当前交易挂载信息中的消费节点公钥不一致");
        }

        flowNodeStateValidator.validateRegisteredAuthorizedAndNotLocked(
                transactionMountMsg.getFlowNodePubkey(),
                centralPubkeyValidator.currentCentralPubkey()
        );
        centralPubkeyValidator.validateCurrentAndNotLocked(transactionMountMsg.getCentralPubkey());
        signatureValidator.validateLowS(transactionMountMsg.getConsumeNodeSignature(), "消费节点签名不符合低S值要求");
        signatureValidator.validateLowS(transactionMountMsg.getFlowNodeSignature(), "流转节点签名不符合低S值要求");

        byte[] verifyData = protocolRawBytesBuilder.transactionMountVerifyData(transactionMountMsg);
        proofOfWorkValidator.validate(verifyData, transactionDifficultyTargetNbits, "前8项数据的hash值不符合注册难度目标要求");
        signatureValidator.validateSignature(verifyData, transactionMountMsg.getConsumeNodeSignature(), transactionMountMsg.getConsumeNodePubkey(), "消费节点签名验证失败");
        signatureValidator.validateSignature(verifyData, transactionMountMsg.getFlowNodeSignature(), transactionMountMsg.getFlowNodePubkey(), "流转节点签名验证失败");
        centralSignatureService.signAndPopulate(
                transactionMountMsg,
                verifyData,
                transactionMountMsg.getConsumeNodeSignature(),
                transactionMountMsg.getFlowNodeSignature()
        );

        TransactionMountMsg transactionMountMsgInDb = transactionMountMsgRepository.save(transactionMountMsg);
        msgAbstractService.saveMsgAbstract(transactionMountMsg);
        consumeChainService.saveConsumeChain(transactionMountMsgInDb, transactionRecordMsg);

        return transactionMountMsgInDb;
    }

    @Override
    public TransactionMountMsg getTransactionMountMsgById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("交易挂载信息id不能为空");
        }

        return transactionMountMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("交易挂载信息id(" + id + ")不存在"));
    }

    @Override
    public TransactionMountMsg getTransactionMountMsgByMountedTransactionRecordId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("挂载的交易记录信息id不能为空");
        }

        TransactionMountMsg transactionMountMsg = transactionMountMsgRepository.findByMountedTransactionRecordId(id);
        if (transactionMountMsg == null) {
            throw new IllegalArgumentException("挂载的交易记录信息id(" + id + ")不存在对应的交易挂载信息");
        }

        return transactionMountMsg;
    }

    @Override
    public List<TransactionMountMsg> getTransactionMountMsgByConsumeNodePubkey(byte[] consumeNodePubkey) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != 33) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        return transactionMountMsgRepository.findByConsumeNodePubkey(consumeNodePubkey);
    }

    @Override
    public List<TransactionMountMsg> getTransactionMountMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionMountMsgRepository.findByFlowNodePubkey(flowNodePubkey);
    }

    @Override
    public List<TransactionMountMsg> getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(
            byte[] consumeNodePubkey,
            byte[] flowNodePubkey
    ) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != 33) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionMountMsgRepository.findByConsumeNodePubkeyAndFlowNodePubkey(consumeNodePubkey, flowNodePubkey);
    }
}
