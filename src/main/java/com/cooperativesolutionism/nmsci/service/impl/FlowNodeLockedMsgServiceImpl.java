package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Service
@Validated
public class FlowNodeLockedMsgServiceImpl implements FlowNodeLockedMsgService {
    @Resource
    private FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;

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

    @Override
    public FlowNodeLockedMsg saveFlowNodeLockedMsg(@Valid @Nonnull FlowNodeLockedMsg flowNodeLockedMsg) {
        if (flowNodeLockedMsg.getMsgType() != MsgTypeEnum.FlowNodeLockedMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.FlowNodeLockedMsg.getValue());
        }

        if (flowNodeLockedMsgRepository.existsById(flowNodeLockedMsg.getId())) {
            throw new IllegalArgumentException("该流转节点公钥冻结信息id(" + flowNodeLockedMsg.getId() + ")已存在");
        }

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

        msgAbstractService.saveMsgAbstract(flowNodeLockedMsg);

        return flowNodeLockedMsgRepository.save(flowNodeLockedMsg);
    }

    @Override
    public FlowNodeLockedMsg getFlowNodeLockedMsgById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("流转节点冻结消息id不能为空");
        }

        return flowNodeLockedMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("流转节点公钥冻结信息id(" + id + ")不存在"));
    }

    @Override
    public FlowNodeLockedMsg getFlowNodeLockedMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不为33字节");
        }

        if (!flowNodeLockedMsgRepository.existsByFlowNodePubkey(flowNodePubkey)) {
            throw new IllegalArgumentException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")未冻结");
        }

        return flowNodeLockedMsgRepository.findByFlowNodePubkey(flowNodePubkey);
    }
}
