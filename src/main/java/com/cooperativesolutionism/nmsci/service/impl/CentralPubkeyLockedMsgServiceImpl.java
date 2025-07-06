package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
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
import java.util.Base64;

@Service
@Validated
public class CentralPubkeyLockedMsgServiceImpl implements CentralPubkeyLockedMsgService {

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Resource
    private CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    @Resource
    private MsgAbstractServiceImpl msgAbstractServiceImpl;

    @Override
    public CentralPubkeyLockedMsg saveCentralPubkeyLockedMsg(@Valid @Nonnull CentralPubkeyLockedMsg centralPubkeyLockedMsg) {
        if (centralPubkeyLockedMsg.getMsgType() != 1) {
            throw new IllegalArgumentException("信息类型错误，必须为1");
        }

        if (centralPubkeyLockedMsgRepository.existsById(centralPubkeyLockedMsg.getId())) {
            throw new IllegalArgumentException("该中心公钥冻结信息id(" + centralPubkeyLockedMsg.getId() + ")已存在");
        }

        if (centralPubkeyLockedMsgRepository.existsByCentralPubkey(centralPubkeyLockedMsg.getCentralPubkey())) {
            throw new IllegalArgumentException("该中心公钥(" + ByteArrayUtil.bytesToBase64(centralPubkeyLockedMsg.getCentralPubkey()) + ")已被冻结");
        }

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        if (!Arrays.equals(centralPubkeyLockedMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误");
        }

        try {
            if (!Secp256k1EncryptUtil.isLowS(centralPubkeyLockedMsg.getCentralSignaturePre())) {
                throw new IllegalArgumentException("中心预签名不符合低S标准");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 拼接验证数据 【信息类型2字节(1)】+【uuid16字节】+【中心公钥33字节】
        byte[] verifyData;
        verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(centralPubkeyLockedMsg.getMsgType()),
                ByteArrayUtil.uuidToBytes(centralPubkeyLockedMsg.getId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                centralPubkeyLockedMsg.getCentralPubkey()
        );

        try {
            boolean isValidSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    centralPubkeyLockedMsg.getCentralSignaturePre(),
                    Secp256k1EncryptUtil.compressedToPublicKey(centralPubkeyLockedMsg.getCentralPubkey())
            );
            if (!isValidSignature) {
                throw new IllegalArgumentException("中心预签名验证失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long timestamp = DateUtil.getCurrentMicros();

        // 拼接中心签名数据 【信息类型2字节(1)】+【uuid16字节】+【中心公钥33字节】+【中心对信息(前3项数据)签名64字节】+【时间戳8字节】
        byte[] centralSignData;
        centralSignData = ArrayUtils.addAll(
                verifyData,
                centralPubkeyLockedMsg.getCentralSignaturePre()
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
            centralPubkeyLockedMsg.setConfirmTimestamp(timestamp);
            centralPubkeyLockedMsg.setCentralSignature(centralSignature);
            centralPubkeyLockedMsg.setRawBytes(rawBytes);
            centralPubkeyLockedMsg.setTxid(MerkleTreeUtil.calcTxid(rawBytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        msgAbstractServiceImpl.saveMsgAbstract(centralPubkeyLockedMsg);

        return centralPubkeyLockedMsgRepository.save(centralPubkeyLockedMsg);
    }
}
