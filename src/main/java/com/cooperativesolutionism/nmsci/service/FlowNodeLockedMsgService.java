package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;

@Service
@Validated
public class FlowNodeLockedMsgService {
    private final FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;
    private final MessageWritePipeline messageWritePipeline;
    private final FlowNodeStateValidator flowNodeStateValidator;
    private final CentralPubkeyValidator centralPubkeyValidator;
    private final SignatureValidator signatureValidator;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;
    private final CentralSignatureService centralSignatureService;
    private final TransactionTemplate transactionTemplate;

    public FlowNodeLockedMsgService(
            FlowNodeLockedMsgRepository flowNodeLockedMsgRepository,
            MessageWritePipeline messageWritePipeline,
            FlowNodeStateValidator flowNodeStateValidator,
            CentralPubkeyValidator centralPubkeyValidator,
            SignatureValidator signatureValidator,
            ProtocolRawBytesBuilder protocolRawBytesBuilder,
            CentralSignatureService centralSignatureService,
            TransactionTemplate transactionTemplate
    ) {
        this.flowNodeLockedMsgRepository = flowNodeLockedMsgRepository;
        this.messageWritePipeline = messageWritePipeline;
        this.flowNodeStateValidator = flowNodeStateValidator;
        this.centralPubkeyValidator = centralPubkeyValidator;
        this.signatureValidator = signatureValidator;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
        this.centralSignatureService = centralSignatureService;
        this.transactionTemplate = transactionTemplate;
    }

    public FlowNodeLockedMsg saveFlowNodeLockedMsg(@Valid @Nonnull FlowNodeLockedMsg flowNodeLockedMsg) {
        messageWritePipeline.requireMsgType(flowNodeLockedMsg, MsgTypeEnum.FlowNodeLockedMsg);
        messageWritePipeline.rejectExistingId(
                flowNodeLockedMsg,
                flowNodeLockedMsgRepository::existsById,
                () -> "该流转节点公钥冻结信息id(" + flowNodeLockedMsg.getId() + ")已存在"
        );

        flowNodeStateValidator.validateRegisteredAuthorizedAndNotLocked(
                flowNodeLockedMsg.getFlowNodePubkey(),
                centralPubkeyValidator.currentCentralPubkey()
        );
        centralPubkeyValidator.validateCurrentAndNotLocked(flowNodeLockedMsg.getCentralPubkey());
        signatureValidator.validateLowS(flowNodeLockedMsg.getFlowNodeSignature(), "流转节点签名不符合低S标准");

        byte[] verifyData = protocolRawBytesBuilder.flowNodeLockedVerifyData(flowNodeLockedMsg);
        signatureValidator.validateSignature(
                verifyData,
                flowNodeLockedMsg.getFlowNodeSignature(),
                flowNodeLockedMsg.getFlowNodePubkey(),
                "流转节点签名验证失败"
        );
        centralSignatureService.signAndPopulate(
                flowNodeLockedMsg,
                verifyData,
                flowNodeLockedMsg.getFlowNodeSignature()
        );

        // 事务收窄（性能审计 H1）：验签/央签在事务外完成；仅将 id 存在性与「注册/授权/未冻结 + 中心公钥未冻结」
        // 冲突复检 + 原子落库收进窄事务。not-locked 在无事务前置校验之后、提交之前仍可能被并发冻结改变，故事务内重做，
        // 同时保证 msg_abstract 与实体两次写入的原子性。
        return transactionTemplate.execute(status -> {
            messageWritePipeline.rejectExistingId(
                    flowNodeLockedMsg,
                    flowNodeLockedMsgRepository::existsById,
                    () -> "该流转节点公钥冻结信息id(" + flowNodeLockedMsg.getId() + ")已存在"
            );
            flowNodeStateValidator.validateRegisteredAuthorizedAndNotLocked(
                    flowNodeLockedMsg.getFlowNodePubkey(),
                    centralPubkeyValidator.currentCentralPubkey()
            );
            centralPubkeyValidator.validateNotLocked(flowNodeLockedMsg.getCentralPubkey());
            return messageWritePipeline.saveAbstractThenEntity(flowNodeLockedMsg);
        });
    }
    public FlowNodeLockedMsg getFlowNodeLockedMsgById(UUID id) {
        return EntityLookup.requireById(id, "流转节点公钥冻结信息", flowNodeLockedMsgRepository::findById);
    }
    public FlowNodeLockedMsg getFlowNodeLockedMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        return findFlowNodeLockedMsgByFlowNodePubkey(flowNodePubkey)
                .orElseThrow(() -> new NotFoundException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")未冻结"));
    }
    public Optional<FlowNodeLockedMsg> findFlowNodeLockedMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不为33字节");
        }

        return Optional.ofNullable(flowNodeLockedMsgRepository.findByFlowNodePubkey(flowNodePubkey));
    }
    @Transactional(readOnly = true)
    public Slice<FlowNodeLockedMsg> listFlowNodeLockedMsgs(Pageable pageable) {
        return flowNodeLockedMsgRepository.findAll(pageable);
    }
}
