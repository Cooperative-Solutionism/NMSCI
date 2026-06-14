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
public class CentralPubkeyEmpowerMsgService {

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Resource
    private FlowNodeStateValidator flowNodeStateValidator;

    @Resource
    private CentralPubkeyValidator centralPubkeyValidator;

    @Resource
    private SignatureValidator signatureValidator;

    @Resource
    private ProtocolRawBytesBuilder protocolRawBytesBuilder;

    @Resource
    private CentralSignatureService centralSignatureService;
    @Transactional
    public CentralPubkeyEmpowerMsg saveCentralPubkeyEmpowerMsg(@Valid @Nonnull CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg) {
        if (centralPubkeyEmpowerMsg.getMsgType() != MsgTypeEnum.CentralPubkeyEmpowerMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.CentralPubkeyEmpowerMsg.getValue());
        }

        if (centralPubkeyEmpowerMsgRepository.existsById(centralPubkeyEmpowerMsg.getId())) {
            throw new ConflictException("该中心公钥公证信息id(" + centralPubkeyEmpowerMsg.getId() + ")已存在");
        }

        flowNodeStateValidator.validateRegistered(centralPubkeyEmpowerMsg.getFlowNodePubkey());
        if (centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(centralPubkeyEmpowerMsg.getFlowNodePubkey())) {
            throw new ConflictException("该流转节点公钥(" + ByteArrayUtil.bytesToBase64(centralPubkeyEmpowerMsg.getFlowNodePubkey()) + ")已进行过授权");
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

        msgAbstractService.saveMsgAbstract(centralPubkeyEmpowerMsg);

        return centralPubkeyEmpowerMsgRepository.save(centralPubkeyEmpowerMsg);
    }
    public CentralPubkeyEmpowerMsg getCentralPubkeyEmpowerMsgById(UUID id) {
        return EntityLookup.requireById(id, "中心公钥公证信息", centralPubkeyEmpowerMsgRepository::findById);
    }
    public CentralPubkeyEmpowerMsg getCentralPubkeyEmpowerMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        if (!centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(flowNodePubkey)) {
            throw new IllegalArgumentException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")未授权");
        }

        return centralPubkeyEmpowerMsgRepository.findByFlowNodePubkey(flowNodePubkey);
    }
    @Transactional(readOnly = true)
    public Slice<CentralPubkeyEmpowerMsg> listCentralPubkeyEmpowerMsgs(byte[] flowNodePubkey, Pageable pageable) {
        if (flowNodePubkey == null) {
            return centralPubkeyEmpowerMsgRepository.findAll(pageable);
        }

        return centralPubkeyEmpowerMsgRepository.findByFlowNodePubkey(flowNodePubkey, pageable);
    }
}
