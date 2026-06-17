package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

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

    private final BlockDifficultyService blockDifficultyService;
    private final TransactionRecordMsgRepository transactionRecordMsgRepository;
    private final MessageWritePipeline messageWritePipeline;
    private final FlowNodeStateValidator flowNodeStateValidator;
    private final CentralPubkeyValidator centralPubkeyValidator;
    private final SignatureValidator signatureValidator;
    private final ProofOfWorkValidator proofOfWorkValidator;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;
    private final CentralSignatureService centralSignatureService;

    public TransactionRecordMsgService(
            BlockDifficultyService blockDifficultyService,
            TransactionRecordMsgRepository transactionRecordMsgRepository,
            MessageWritePipeline messageWritePipeline,
            FlowNodeStateValidator flowNodeStateValidator,
            CentralPubkeyValidator centralPubkeyValidator,
            SignatureValidator signatureValidator,
            ProofOfWorkValidator proofOfWorkValidator,
            ProtocolRawBytesBuilder protocolRawBytesBuilder,
            CentralSignatureService centralSignatureService
    ) {
        this.blockDifficultyService = blockDifficultyService;
        this.transactionRecordMsgRepository = transactionRecordMsgRepository;
        this.messageWritePipeline = messageWritePipeline;
        this.flowNodeStateValidator = flowNodeStateValidator;
        this.centralPubkeyValidator = centralPubkeyValidator;
        this.signatureValidator = signatureValidator;
        this.proofOfWorkValidator = proofOfWorkValidator;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
        this.centralSignatureService = centralSignatureService;
    }

    @Transactional
    public TransactionRecordMsg saveTransactionRecordMsg(@Valid @Nonnull TransactionRecordMsg transactionRecordMsg) {
        messageWritePipeline.requireMsgType(transactionRecordMsg, MsgTypeEnum.TransactionRecordMsg);

        if (transactionRecordMsg.getAmount() == null || transactionRecordMsg.getAmount() <= 0) {
            throw new IllegalArgumentException("交易金额必须为正数");
        }

        messageWritePipeline.rejectExistingId(
                transactionRecordMsg,
                transactionRecordMsgRepository::existsById,
                () -> "该交易记录信息id(" + transactionRecordMsg.getId() + ")已存在"
        );

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

        return messageWritePipeline.saveAbstractThenEntity(transactionRecordMsg, transactionRecordMsgRepository::save);
    }
    public TransactionRecordMsg getTransactionRecordMsgById(UUID id) {
        return EntityLookup.requireById(id, "交易记录信息", transactionRecordMsgRepository::findById);
    }
    public Slice<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByConsumeNodePubkey(consumeNodePubkey, pageable);
    }
    public Slice<TransactionRecordMsg> getTransactionRecordMsgByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable) {
        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByFlowNodePubkey(flowNodePubkey, pageable);
    }
    public Slice<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
            byte[] consumeNodePubkey,
            byte[] flowNodePubkey,
            Pageable pageable
    ) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByConsumeNodePubkeyAndFlowNodePubkey(consumeNodePubkey, flowNodePubkey, pageable);
    }
    public Slice<TransactionRecordMsg> searchTransactionRecordMsgs(
            byte[] consumeNodePubkey,
            byte[] flowNodePubkey,
            Short currencyType,
            Long startTime,
            Long endTime,
            Pageable pageable
    ) {
        return transactionRecordMsgRepository.search(
                consumeNodePubkey,
                flowNodePubkey,
                currencyType,
                startTime,
                endTime,
                pageable
        );
    }

}
