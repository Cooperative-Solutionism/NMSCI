package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
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
public class CentralPubkeyEmpowerMsgService {

    private final CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;
    private final MessageWritePipeline messageWritePipeline;
    private final FlowNodeStateValidator flowNodeStateValidator;
    private final CentralPubkeyValidator centralPubkeyValidator;
    private final SignatureValidator signatureValidator;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;
    private final CentralSignatureService centralSignatureService;

    public CentralPubkeyEmpowerMsgService(
            CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository,
            MessageWritePipeline messageWritePipeline,
            FlowNodeStateValidator flowNodeStateValidator,
            CentralPubkeyValidator centralPubkeyValidator,
            SignatureValidator signatureValidator,
            ProtocolRawBytesBuilder protocolRawBytesBuilder,
            CentralSignatureService centralSignatureService
    ) {
        this.centralPubkeyEmpowerMsgRepository = centralPubkeyEmpowerMsgRepository;
        this.messageWritePipeline = messageWritePipeline;
        this.flowNodeStateValidator = flowNodeStateValidator;
        this.centralPubkeyValidator = centralPubkeyValidator;
        this.signatureValidator = signatureValidator;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
        this.centralSignatureService = centralSignatureService;
    }

    @Transactional
    public CentralPubkeyEmpowerMsg saveCentralPubkeyEmpowerMsg(@Valid @Nonnull CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg) {
        messageWritePipeline.requireMsgType(centralPubkeyEmpowerMsg, MsgTypeEnum.CentralPubkeyEmpowerMsg);
        messageWritePipeline.rejectExistingId(
                centralPubkeyEmpowerMsg,
                centralPubkeyEmpowerMsgRepository::existsById,
                () -> "该中心公钥公证信息id(" + centralPubkeyEmpowerMsg.getId() + ")已存在"
        );

        flowNodeStateValidator.validateRegistered(centralPubkeyEmpowerMsg.getFlowNodePubkey());
        // 公证唯一性按 (流转节点公钥, 中心公钥) 组合判定：同一节点对同一中心公钥不可重复授权，
        // 但中心公钥被冻结/轮换后可对新的中心公钥重新授权（对齐 PROTOCOL.md 轮换连续性；下方
        // validateCurrentAndNotLocked 已确保只能对当前未冻结的中心公钥授权）。
        if (centralPubkeyEmpowerMsgRepository.countByFlowNodePubkeyAndCentralPubkey(
                centralPubkeyEmpowerMsg.getFlowNodePubkey(),
                centralPubkeyEmpowerMsg.getCentralPubkey()) > 0) {
            throw new ConflictException("该流转节点公钥(" + ByteArrayUtil.bytesToBase64(centralPubkeyEmpowerMsg.getFlowNodePubkey())
                    + ")已对该中心公钥授权过");
        }
        centralPubkeyValidator.validateCurrentAndNotLocked(centralPubkeyEmpowerMsg.getCentralPubkey());
        signatureValidator.validateLowS(centralPubkeyEmpowerMsg.getFlowNodeSignature(), "流转节点签名不符合低S标准");

        byte[] verifyData = protocolRawBytesBuilder.centralPubkeyEmpowerVerifyData(centralPubkeyEmpowerMsg);
        signatureValidator.validateSignature(
                verifyData,
                centralPubkeyEmpowerMsg.getFlowNodeSignature(),
                centralPubkeyEmpowerMsg.getFlowNodePubkey(),
                "流转节点签名验证失败"
        );
        centralSignatureService.signAndPopulate(
                centralPubkeyEmpowerMsg,
                verifyData,
                centralPubkeyEmpowerMsg.getFlowNodeSignature()
        );

        return messageWritePipeline.saveAbstractThenEntity(centralPubkeyEmpowerMsg, centralPubkeyEmpowerMsgRepository::save);
    }
    public CentralPubkeyEmpowerMsg getCentralPubkeyEmpowerMsgById(UUID id) {
        return EntityLookup.requireById(id, "中心公钥公证信息", centralPubkeyEmpowerMsgRepository::findById);
    }
    @Transactional(readOnly = true)
    public Slice<CentralPubkeyEmpowerMsg> listCentralPubkeyEmpowerMsgs(byte[] flowNodePubkey, Pageable pageable) {
        if (flowNodePubkey == null) {
            return centralPubkeyEmpowerMsgRepository.findAll(pageable);
        }

        return centralPubkeyEmpowerMsgRepository.findByFlowNodePubkey(flowNodePubkey, pageable);
    }
}
