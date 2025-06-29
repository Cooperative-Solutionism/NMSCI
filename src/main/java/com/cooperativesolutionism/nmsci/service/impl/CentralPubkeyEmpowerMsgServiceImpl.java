package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
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
import java.util.Base64;

@Service
@Validated
public class CentralPubkeyEmpowerMsgServiceImpl implements CentralPubkeyEmpowerMsgService {

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Override
    public CentralPubkeyEmpowerMsg saveCentralPubkeyEmpowerMsg(@Valid @Nonnull CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg) {
        if (centralPubkeyEmpowerMsg.getMsgType() != 0) {
            throw new IllegalArgumentException("信息类型错误，必须为0");
        }

        if (centralPubkeyEmpowerMsgRepository.existsById(centralPubkeyEmpowerMsg.getId())) {
            throw new IllegalArgumentException("该中心公钥公证信息id(" + centralPubkeyEmpowerMsg.getId() + ")已存在");
        }

        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(centralPubkeyEmpowerMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + Base64.getEncoder().encodeToString(centralPubkeyEmpowerMsg.getFlowNodePubkey()) + ")未注册");
        }

        if (centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(centralPubkeyEmpowerMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + Base64.getEncoder().encodeToString(centralPubkeyEmpowerMsg.getFlowNodePubkey()) + ")已进行过授权");
        }

        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
        if (!Arrays.equals(centralPubkeyEmpowerMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误");
        }

        try {
            if (!Secp256k1EncryptUtil.isLowS(centralPubkeyEmpowerMsg.getFlowNodeSignature())) {
                throw new IllegalArgumentException("流转节点签名不符合低S标准");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 拼接验证数据 【信息类型2字节(0)】+【uuid16字节】+【流转节点公钥33字节】+【中心公钥33字节】
        byte[] verifyData;
        verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(centralPubkeyEmpowerMsg.getMsgType()),
                ByteArrayUtil.uuidToBytes(centralPubkeyEmpowerMsg.getId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                centralPubkeyEmpowerMsg.getFlowNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                centralPubkeyEmpowerMsg.getCentralPubkey()
        );

        try {
            boolean isValidSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    centralPubkeyEmpowerMsg.getFlowNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(centralPubkeyEmpowerMsg.getFlowNodePubkey())
            );
            if (!isValidSignature) {
                throw new IllegalArgumentException("流转节点签名验证失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long timestamp = DateUtil.getCurrentMicros();

        // 拼接中心签名数据 【信息类型2字节(0)】+【uuid16字节】+【流转节点公钥33字节】+【中心公钥33字节】+【流转节点对信息(前4项数据)签名64字节】+【时间戳8字节】
        byte[] centralSignData;
        centralSignData = ArrayUtils.addAll(
                verifyData,
                centralPubkeyEmpowerMsg.getFlowNodeSignature()
        );
        centralSignData = ArrayUtils.addAll(
                centralSignData,
                ByteArrayUtil.longToBytes(timestamp)
        );

        try {
            byte[] centralPrikey = Base64.getDecoder().decode(centralPrikeyBase64);
            byte[] centralSignature = Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey));
            centralPubkeyEmpowerMsg.setConfirmTimestamp(timestamp);
            centralPubkeyEmpowerMsg.setCentralSignature(centralSignature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return centralPubkeyEmpowerMsgRepository.save(centralPubkeyEmpowerMsg);
    }
}
