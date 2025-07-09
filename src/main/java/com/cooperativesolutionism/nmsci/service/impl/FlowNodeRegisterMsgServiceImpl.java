package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.util.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

@Service
@Validated
public class FlowNodeRegisterMsgServiceImpl implements FlowNodeRegisterMsgService {

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Value("${register-difficulty-target-nbits}")
    private int registerDifficultyTargetNbits;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Override
    public FlowNodeRegisterMsg saveFlowNodeRegisterMsg(@Valid @Nonnull FlowNodeRegisterMsg flowNodeRegisterMsg) {
        if (flowNodeRegisterMsg.getMsgType() != 2) {
            throw new IllegalArgumentException("信息类型错误，必须为2");
        }

        if (flowNodeRegisterMsgRepository.existsById(flowNodeRegisterMsg.getId())) {
            throw new IllegalArgumentException("该流转节点注册信息id(" + flowNodeRegisterMsg.getId() + ")已存在");
        }

        if (!flowNodeRegisterMsg.getRegisterDifficultyTarget().equals(registerDifficultyTargetNbits)) {
            throw new IllegalArgumentException("注册难度目标与前区块中的注册难度目标不一致");
        }

        if (flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodeRegisterMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + ByteArrayUtil.bytesToBase64(flowNodeRegisterMsg.getFlowNodePubkey()) + ")已被注册");
        }

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        if (!Arrays.equals(flowNodeRegisterMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误");
        }

        try {
            if (Secp256k1EncryptUtil.isNotLowS(flowNodeRegisterMsg.getFlowNodeSignature())) {
                throw new IllegalArgumentException("流转节点签名不符合低S值要求");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 拼接验证数据 【信息类型2字节(2)】+【uuid16字节】+【注册难度目标4字节】+【随机数4字节】+【流转节点公钥33字节】+【中心公钥33字节】
        byte[] verifyData;
        verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(flowNodeRegisterMsg.getMsgType()),
                ByteArrayUtil.uuidToBytes(flowNodeRegisterMsg.getId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.intToBytes(flowNodeRegisterMsg.getRegisterDifficultyTarget())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.intToBytes(flowNodeRegisterMsg.getNonce())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                flowNodeRegisterMsg.getFlowNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                flowNodeRegisterMsg.getCentralPubkey()
        );

        BigInteger registerDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));
        BigInteger verifyDataHash = new BigInteger(1, Sha256Util.doubleDigest(verifyData));
        if (verifyDataHash.compareTo(registerDifficultyTarget) > 0) {
            throw new IllegalArgumentException("前6项数据的hash值不符合注册难度目标要求");
        }

        try {
            boolean isValidSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    flowNodeRegisterMsg.getFlowNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(flowNodeRegisterMsg.getFlowNodePubkey())
            );
            if (!isValidSignature) {
                throw new IllegalArgumentException("流转节点签名验证失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long timestamp = DateUtil.getCurrentMicros();

        // 拼接中心签名数据 【信息类型2字节(2)】+【uuid16字节】+【注册难度目标4字节】+【随机数4字节】+【流转节点公钥33字节】+【中心公钥33字节】+【流转节点对信息(前6项数据)签名64字节】+【时间戳8字节】
        byte[] centralSignData;
        centralSignData = ArrayUtils.addAll(
                verifyData,
                flowNodeRegisterMsg.getFlowNodeSignature()
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
            flowNodeRegisterMsg.setConfirmTimestamp(timestamp);
            flowNodeRegisterMsg.setCentralSignature(centralSignature);
            flowNodeRegisterMsg.setRawBytes(rawBytes);
            flowNodeRegisterMsg.setTxid(MerkleTreeUtil.calcTxid(rawBytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        msgAbstractService.saveMsgAbstract(flowNodeRegisterMsg);

        return flowNodeRegisterMsgRepository.save(flowNodeRegisterMsg);
    }
}
