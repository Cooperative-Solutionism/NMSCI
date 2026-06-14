package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.dto.FlowNodeStateResponseDTO;
import com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
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
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
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

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Resource
    private CentralPubkeyValidator centralPubkeyValidator;

    @Resource
    private SignatureValidator signatureValidator;

    @Resource
    private ProofOfWorkValidator proofOfWorkValidator;

    @Resource
    private ProtocolRawBytesBuilder protocolRawBytesBuilder;
    @Transactional
    public FlowNodeRegisterMsg saveFlowNodeRegisterMsg(@Valid @Nonnull FlowNodeRegisterMsg flowNodeRegisterMsg) {
        if (flowNodeRegisterMsg.getMsgType() != MsgTypeEnum.FlowNodeRegisterMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        }

        if (flowNodeRegisterMsgRepository.existsById(flowNodeRegisterMsg.getId())) {
            throw new IllegalArgumentException("该流转节点注册信息id(" + flowNodeRegisterMsg.getId() + ")已存在");
        }

        BlockInfo newestBlockInfo = blockInfoRepository.findTopByOrderByHeightDesc();
        int registerDifficultyTargetNbits = newestBlockInfo.getRegisterDifficultyTarget();
        if (!flowNodeRegisterMsg.getRegisterDifficultyTarget().equals(registerDifficultyTargetNbits)) {
            throw new IllegalArgumentException("注册难度目标与前区块中的注册难度目标不一致");
        }

        if (flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodeRegisterMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + ByteArrayUtil.bytesToBase64(flowNodeRegisterMsg.getFlowNodePubkey()) + ")已被注册");
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

        // 拼接注册信息原始字节数据 = 验证数据 + 流转节点对信息签名64字节
        byte[] rawBytes = ArrayUtils.addAll(verifyData, flowNodeRegisterMsg.getFlowNodeSignature());
        flowNodeRegisterMsg.setRawBytes(rawBytes);
        flowNodeRegisterMsg.setTxid(MerkleTreeUtil.calcTxid(rawBytes));

        msgAbstractService.saveMsgAbstract(flowNodeRegisterMsg);

        return flowNodeRegisterMsgRepository.save(flowNodeRegisterMsg);
    }
    public FlowNodeRegisterMsg getFlowNodeRegisterMsgById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("流转节点注册信息id不能为空");
        }

        return flowNodeRegisterMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("流转节点注册信息id(" + id + ")不存在"));
    }
    public FlowNodeRegisterMsg getFlowNodeRegisterMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不为33字节");
        }

        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodePubkey)) {
            throw new IllegalArgumentException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")不存在");
        }

        return flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(flowNodePubkey);
    }
    @Transactional(readOnly = true)
    public FlowNodeStateResponseDTO getFlowNodeState(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
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
