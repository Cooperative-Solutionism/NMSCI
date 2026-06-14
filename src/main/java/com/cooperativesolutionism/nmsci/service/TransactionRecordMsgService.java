package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.protocol.BlockDifficultyService;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProofOfWorkValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Service
@Validated
public class TransactionRecordMsgService {

    @Resource
    private BlockDifficultyService blockDifficultyService;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

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
    @Transactional
    public TransactionRecordMsg saveTransactionRecordMsg(@Valid @Nonnull TransactionRecordMsg transactionRecordMsg) {
        if (transactionRecordMsg.getMsgType() != MsgTypeEnum.TransactionRecordMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.TransactionRecordMsg.getValue());
        }

        if (transactionRecordMsg.getAmount() == null || transactionRecordMsg.getAmount() <= 0) {
            throw new IllegalArgumentException("交易金额必须为正数");
        }

        if (transactionRecordMsgRepository.existsById(transactionRecordMsg.getId())) {
            throw new IllegalArgumentException("该交易记录信息id(" + transactionRecordMsg.getId() + ")已存在");
        }

        if (!CurrencyTypeEnum.containsValue(transactionRecordMsg.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        int transactionDifficultyTargetNbits = blockDifficultyService.currentTransactionDifficultyTarget();
        if (!transactionRecordMsg.getTransactionDifficultyTarget().equals(transactionDifficultyTargetNbits)) {
            throw new IllegalArgumentException("交易难度目标与前区块中的交易难度目标不一致");
        }

        flowNodeStateValidator.validateRegisteredAuthorizedAndNotLocked(
                transactionRecordMsg.getFlowNodePubkey(),
                centralPubkeyValidator.currentCentralPubkey()
        );
        centralPubkeyValidator.validateCurrentAndNotLocked(transactionRecordMsg.getCentralPubkey());
        signatureValidator.validateLowS(transactionRecordMsg.getConsumeNodeSignature(), "消费节点签名不符合低S值要求");
        signatureValidator.validateLowS(transactionRecordMsg.getFlowNodeSignature(), "流转节点签名不符合低S值要求");

        byte[] verifyData = protocolRawBytesBuilder.transactionRecordVerifyData(transactionRecordMsg);
        proofOfWorkValidator.validate(verifyData, transactionDifficultyTargetNbits, "前9项数据的hash值不符合注册难度目标要求");
        signatureValidator.validateSignature(verifyData, transactionRecordMsg.getConsumeNodeSignature(), transactionRecordMsg.getConsumeNodePubkey(), "消费节点签名验证失败");
        signatureValidator.validateSignature(verifyData, transactionRecordMsg.getFlowNodeSignature(), transactionRecordMsg.getFlowNodePubkey(), "流转节点签名验证失败");
        centralSignatureService.signAndPopulate(
                transactionRecordMsg,
                verifyData,
                transactionRecordMsg.getConsumeNodeSignature(),
                transactionRecordMsg.getFlowNodeSignature()
        );

        msgAbstractService.saveMsgAbstract(transactionRecordMsg);

        return transactionRecordMsgRepository.save(transactionRecordMsg);
    }
    public TransactionRecordMsg getTransactionRecordMsgById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("交易记录信息id不能为空");
        }

        return transactionRecordMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("交易记录信息id(" + id + ")不存在"));
    }
    public Slice<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != 33) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByConsumeNodePubkey(consumeNodePubkey, pageable);
    }
    public Slice<TransactionRecordMsg> getTransactionRecordMsgByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByFlowNodePubkey(flowNodePubkey, pageable);
    }
    public Slice<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
            byte[] consumeNodePubkey,
            byte[] flowNodePubkey,
            Pageable pageable
    ) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != 33) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByConsumeNodePubkeyAndFlowNodePubkey(consumeNodePubkey, flowNodePubkey, pageable);
    }

}
