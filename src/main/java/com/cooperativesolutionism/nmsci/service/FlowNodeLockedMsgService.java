package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
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
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;

@Service
@Validated
public class FlowNodeLockedMsgService {
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
    @Transactional
    public FlowNodeLockedMsg saveFlowNodeLockedMsg(@Valid @Nonnull FlowNodeLockedMsg flowNodeLockedMsg) {
        if (flowNodeLockedMsg.getMsgType() != MsgTypeEnum.FlowNodeLockedMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.FlowNodeLockedMsg.getValue());
        }

        if (flowNodeLockedMsgRepository.existsById(flowNodeLockedMsg.getId())) {
            throw new ConflictException("该流转节点公钥冻结信息id(" + flowNodeLockedMsg.getId() + ")已存在");
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
