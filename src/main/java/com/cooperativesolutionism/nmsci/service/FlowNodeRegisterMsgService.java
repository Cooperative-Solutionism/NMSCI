package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

import com.cooperativesolutionism.nmsci.dto.FlowNodeStateResponseDTO;
import com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.ProofOfWorkValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.util.*;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
public class FlowNodeRegisterMsgService {

    private final BlockInfoRepository blockInfoRepository;
    private final FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;
    private final MessageWritePipeline messageWritePipeline;
    private final CentralPubkeyValidator centralPubkeyValidator;
    private final SignatureValidator signatureValidator;
    private final ProofOfWorkValidator proofOfWorkValidator;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;

    public FlowNodeRegisterMsgService(
            BlockInfoRepository blockInfoRepository,
            FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository,
            MessageWritePipeline messageWritePipeline,
            CentralPubkeyValidator centralPubkeyValidator,
            SignatureValidator signatureValidator,
            ProofOfWorkValidator proofOfWorkValidator,
            ProtocolRawBytesBuilder protocolRawBytesBuilder
    ) {
        this.blockInfoRepository = blockInfoRepository;
        this.flowNodeRegisterMsgRepository = flowNodeRegisterMsgRepository;
        this.messageWritePipeline = messageWritePipeline;
        this.centralPubkeyValidator = centralPubkeyValidator;
        this.signatureValidator = signatureValidator;
        this.proofOfWorkValidator = proofOfWorkValidator;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
    }

    @Transactional
    public FlowNodeRegisterMsg saveFlowNodeRegisterMsg(@Valid @Nonnull FlowNodeRegisterMsg flowNodeRegisterMsg) {
        messageWritePipeline.requireMsgType(flowNodeRegisterMsg, MsgTypeEnum.FlowNodeRegisterMsg);
        messageWritePipeline.rejectExistingId(
                flowNodeRegisterMsg,
                flowNodeRegisterMsgRepository::existsById,
                () -> "该流转节点注册信息id(" + flowNodeRegisterMsg.getId() + ")已存在"
        );

        BlockInfo newestBlockInfo = blockInfoRepository.findTopByOrderByHeightDesc();
        if (newestBlockInfo == null) {
            throw new ConflictException("区块链尚未初始化，无法注册流转节点");
        }
        int registerDifficultyTargetNbits = newestBlockInfo.getRegisterDifficultyTarget();
        if (!flowNodeRegisterMsg.getRegisterDifficultyTarget().equals(registerDifficultyTargetNbits)) {
            throw new IllegalArgumentException("注册难度目标与前区块中的注册难度目标不一致");
        }

        if (flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodeRegisterMsg.getFlowNodePubkey())) {
            throw new ConflictException("该流转节点公钥(" + ByteArrayUtil.bytesToBase64(flowNodeRegisterMsg.getFlowNodePubkey()) + ")已被注册");
        }

        signatureValidator.validateLowS(flowNodeRegisterMsg.getFlowNodeSignature(), "流转节点签名不符合低S值要求");

        byte[] verifyData = protocolRawBytesBuilder.flowNodeRegisterVerifyData(flowNodeRegisterMsg);
        proofOfWorkValidator.validate(verifyData, registerDifficultyTargetNbits, "前5项数据的hash值不符合注册难度目标要求");
        signatureValidator.validateSignature(
                verifyData,
                flowNodeRegisterMsg.getFlowNodeSignature(),
                flowNodeRegisterMsg.getFlowNodePubkey(),
                "流转节点签名验证失败"
        );

        messageWritePipeline.populateRawBytes(flowNodeRegisterMsg, verifyData, flowNodeRegisterMsg.getFlowNodeSignature());

        return messageWritePipeline.saveAbstractThenEntity(flowNodeRegisterMsg, flowNodeRegisterMsgRepository::save);
    }
    public FlowNodeRegisterMsg getFlowNodeRegisterMsgById(UUID id) {
        return EntityLookup.requireById(id, "流转节点注册信息", flowNodeRegisterMsgRepository::findById);
    }
    public FlowNodeRegisterMsg getFlowNodeRegisterMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不为33字节");
        }

        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodePubkey)) {
            throw new NotFoundException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")不存在");
        }

        return flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(flowNodePubkey);
    }
    @Transactional(readOnly = true)
    public Slice<FlowNodeRegisterMsg> listFlowNodeRegisterMsgs(byte[] flowNodePubkey, Pageable pageable) {
        if (flowNodePubkey == null) {
            return flowNodeRegisterMsgRepository.findAll(pageable);
        }

        return flowNodeRegisterMsgRepository.findByFlowNodePubkey(flowNodePubkey, pageable);
    }
    @Transactional(readOnly = true)
    public FlowNodeStateResponseDTO getFlowNodeState(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不为33字节");
        }

        return FlowNodeStateResponseDTO.from(flowNodeRegisterMsgRepository.findFlowNodeStateOverview(
                flowNodePubkey,
                centralPubkeyValidator.currentCentralPubkey()
        ));
    }
    @Transactional(readOnly = true)
    public Slice<FlowNodeListItemDTO> listFlowNodes(Boolean registered, Boolean authorized, Boolean locked, Pageable pageable) {
        if (pageable == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }

        if (Boolean.FALSE.equals(registered)) {
            return new SliceImpl<>(List.of(), pageable, false);
        }

        return flowNodeRegisterMsgRepository.findFlowNodeList(
                authorized,
                locked,
                centralPubkeyValidator.currentCentralPubkey(),
                pageable
        );
    }
}
