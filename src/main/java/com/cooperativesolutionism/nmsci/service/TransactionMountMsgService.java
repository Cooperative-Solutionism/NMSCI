package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.protocol.BlockDifficultyService;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProofOfWorkValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Service
@Validated
public class TransactionMountMsgService {
    private final BlockDifficultyService blockDifficultyService;
    private final TransactionMountMsgRepository transactionMountMsgRepository;
    private final MessageWritePipeline messageWritePipeline;
    private final TransactionMountWriteService transactionMountWriteService;
    private final FlowNodeStateValidator flowNodeStateValidator;
    private final CentralPubkeyValidator centralPubkeyValidator;
    private final SignatureValidator signatureValidator;
    private final ProofOfWorkValidator proofOfWorkValidator;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;
    private final CentralSignatureService centralSignatureService;

    public TransactionMountMsgService(
            BlockDifficultyService blockDifficultyService,
            TransactionMountMsgRepository transactionMountMsgRepository,
            MessageWritePipeline messageWritePipeline,
            TransactionMountWriteService transactionMountWriteService,
            FlowNodeStateValidator flowNodeStateValidator,
            CentralPubkeyValidator centralPubkeyValidator,
            SignatureValidator signatureValidator,
            ProofOfWorkValidator proofOfWorkValidator,
            ProtocolRawBytesBuilder protocolRawBytesBuilder,
            CentralSignatureService centralSignatureService
    ) {
        this.blockDifficultyService = blockDifficultyService;
        this.transactionMountMsgRepository = transactionMountMsgRepository;
        this.messageWritePipeline = messageWritePipeline;
        this.transactionMountWriteService = transactionMountWriteService;
        this.flowNodeStateValidator = flowNodeStateValidator;
        this.centralPubkeyValidator = centralPubkeyValidator;
        this.signatureValidator = signatureValidator;
        this.proofOfWorkValidator = proofOfWorkValidator;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
        this.centralSignatureService = centralSignatureService;
    }

    public TransactionMountMsg saveTransactionMountMsg(@Valid @Nonnull TransactionMountMsg transactionMountMsg) {
        messageWritePipeline.requireMsgType(transactionMountMsg, MsgTypeEnum.TransactionMountMsg);

        messageWritePipeline.rejectExistingId(
                transactionMountMsg,
                transactionMountMsgRepository::existsById,
                () -> "该交易挂载信息id(" + transactionMountMsg.getId() + ")已存在"
        );

        int transactionDifficultyTargetNbits = blockDifficultyService.currentTransactionDifficultyTarget();
        if (!transactionMountMsg.getTransactionDifficultyTarget().equals(transactionDifficultyTargetNbits)) {
            throw new IllegalArgumentException("交易难度目标与前区块中的交易难度目标不一致");
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

        return transactionMountWriteService.saveAndAllocate(transactionMountMsg);
    }
    public TransactionMountMsg getTransactionMountMsgById(UUID id) {
        return EntityLookup.requireById(id, "交易挂载信息", transactionMountMsgRepository::findById);
    }
    public TransactionMountMsg getTransactionMountMsgByMountedTransactionRecordId(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("挂载的交易记录信息id不能为空");
        }

        TransactionMountMsg transactionMountMsg = transactionMountMsgRepository.findByMountedTransactionRecordId(id);
        if (transactionMountMsg == null) {
            throw new NotFoundException("挂载的交易记录信息id(" + id + ")不存在对应的交易挂载信息");
        }

        return transactionMountMsg;
    }
    public Slice<TransactionMountMsg> getTransactionMountMsgByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        return transactionMountMsgRepository.findByConsumeNodePubkey(consumeNodePubkey, pageable);
    }
    public Slice<TransactionMountMsg> getTransactionMountMsgByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable) {
        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionMountMsgRepository.findByFlowNodePubkey(flowNodePubkey, pageable);
    }
    public Slice<TransactionMountMsg> getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(
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

        return transactionMountMsgRepository.findByConsumeNodePubkeyAndFlowNodePubkey(consumeNodePubkey, flowNodePubkey, pageable);
    }
    public Slice<TransactionMountMsg> searchTransactionMountMsgs(
            byte[] consumeNodePubkey,
            byte[] flowNodePubkey,
            UUID mountedTransactionRecordId,
            Long startTime,
            Long endTime,
            Pageable pageable
    ) {
        return transactionMountMsgRepository.search(
                consumeNodePubkey,
                flowNodePubkey,
                mountedTransactionRecordId,
                startTime,
                endTime,
                pageable
        );
    }
}
