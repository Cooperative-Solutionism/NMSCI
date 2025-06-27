package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.util.Arrays;

@Service
@Validated
public class CentralPubkeyEmpowerMsgServiceImpl implements CentralPubkeyEmpowerMsgService {

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Override
    public CentralPubkeyEmpowerMsg saveCentralPubkeyEmpowerMsg(@Valid CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg) {
        if (centralPubkeyEmpowerMsg == null) {
            throw new IllegalArgumentException("信息不能为空");
        }

        if (centralPubkeyEmpowerMsg.getMsgType() != 0) {
            throw new IllegalArgumentException("信息类型错误，必须为0");
        }

        if (centralPubkeyEmpowerMsgRepository.existsById(centralPubkeyEmpowerMsg.getId())) {
            throw new IllegalArgumentException("该中心公钥公证信息id(" + centralPubkeyEmpowerMsg.getId() + ")已存在");
        }

        if (centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(centralPubkeyEmpowerMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥已进行过授权");
        }

        // 中心公钥是否正确
        // TODO: 这里的中心公钥需要从配置文件或其他安全方式获取
        if (!Arrays.equals(centralPubkeyEmpowerMsg.getCentralPubkey(), new byte[]{2, 121, -94, 55, -55, 94, -40, 74, 12, 106, 76, -92, 61, -25, 78, 62, 88, -94, -115, 101, -97, -47, -68, -64, 63, -118, 84, -70, -118, -92, 107, -21, -55})) {
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

        // 验证流转节点签名
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

        // 当前时间戳UTC
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
            // TODO: 这里的中心私钥需要从配置文件或其他安全方式获取
            byte[] centralSignature = Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(new byte[]{
                    -47, -48, 51, -79, 0, 100, -61, -74, 95, -110, 112, 118, -105, 41, 95, -88, -57, -108, -59, 21, 28, 37, -65, 108, -86, 18, 15, -61, -101, -54, 4, 97
            }));
            centralPubkeyEmpowerMsg.setConfirmTimestamp(timestamp);
            centralPubkeyEmpowerMsg.setCentralSignature(centralSignature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return centralPubkeyEmpowerMsgRepository.save(centralPubkeyEmpowerMsg);
    }
}
