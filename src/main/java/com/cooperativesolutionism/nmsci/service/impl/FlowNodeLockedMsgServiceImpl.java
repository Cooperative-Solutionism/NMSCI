package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.util.Arrays;

@Service
@Validated
public class FlowNodeLockedMsgServiceImpl implements FlowNodeLockedMsgService {
    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Resource
    private FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Override
    public FlowNodeLockedMsg saveFlowNodeLockedMsg(@Valid @Nonnull FlowNodeLockedMsg flowNodeLockedMsg) {
        if (flowNodeLockedMsg.getMsgType() != MsgTypeEnum.FlowNodeLockedMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.FlowNodeLockedMsg.getValue());
        }

        if (flowNodeLockedMsgRepository.existsById(flowNodeLockedMsg.getId())) {
            throw new IllegalArgumentException("该流转节点公钥冻结信息id(" + flowNodeLockedMsg.getId() + ")已存在");
        }

        // 验证流转节点公钥是否已注册
        String flowNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(flowNodeLockedMsg.getFlowNodePubkey());
        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodeLockedMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未注册");
        }

        // 验证流转节点公钥是否已授权
        if (!centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(flowNodeLockedMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未授权");
        }

        // 验证流转节点公钥是否已冻结
        if (flowNodeLockedMsgRepository.existsByFlowNodePubkey(flowNodeLockedMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")已冻结");
        }

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        if (!Arrays.equals(flowNodeLockedMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误");
        }

        try {
            if (Secp256k1EncryptUtil.isNotLowS(flowNodeLockedMsg.getFlowNodeSignature())) {
                throw new IllegalArgumentException("流转节点签名不符合低S标准");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 拼接验证数据 【信息类型2字节(3)】+【uuid16字节】+【流转节点公钥33字节】+【中心公钥33字节】
        byte[] verifyData;
        verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(flowNodeLockedMsg.getMsgType()),
                ByteArrayUtil.uuidToBytes(flowNodeLockedMsg.getId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                flowNodeLockedMsg.getFlowNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                flowNodeLockedMsg.getCentralPubkey()
        );

        try {
            boolean isValidSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    flowNodeLockedMsg.getFlowNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(flowNodeLockedMsg.getFlowNodePubkey())
            );
            if (!isValidSignature) {
                throw new IllegalArgumentException("流转节点签名验证失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long timestamp = DateUtil.getCurrentMicros();

        // 拼接中心签名数据 【信息类型2字节(3)】+【uuid16字节】+【流转节点公钥33字节】+【中心公钥33字节】+【流转节点对信息(前4项数据)签名64字节】+【时间戳8字节】
        byte[] centralSignData;
        centralSignData = ArrayUtils.addAll(
                verifyData,
                flowNodeLockedMsg.getFlowNodeSignature()
        );
        centralSignData = ArrayUtils.addAll(
                centralSignData,
                ByteArrayUtil.longToBytes(timestamp)
        );

        try {
            byte[] centralPrikey = ByteArrayUtil.base64ToBytes(centralPrikeyBase64);
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey)));
            byte[] rawBytes = ArrayUtils.addAll(
                    centralSignData,
                    centralSignature
            );
            flowNodeLockedMsg.setConfirmTimestamp(timestamp);
            flowNodeLockedMsg.setCentralSignature(centralSignature);
            flowNodeLockedMsg.setRawBytes(rawBytes);
            flowNodeLockedMsg.setTxid(MerkleTreeUtil.calcTxid(rawBytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        msgAbstractService.saveMsgAbstract(flowNodeLockedMsg);

        return flowNodeLockedMsgRepository.save(flowNodeLockedMsg);
    }
}
