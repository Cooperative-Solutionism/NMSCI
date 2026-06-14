package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
public class CentralPubkeyLockedMsgService {

    private static final Logger logger = LoggerFactory.getLogger(CentralPubkeyLockedMsgService.class);

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    @Resource
    private CentralPubkeyLockedMsgPersistenceService centralPubkeyLockedMsgPersistenceService;

    @Resource
    private BlockChainService blockChainService;
    public void saveCentralPubkeyLockedMsg(@Valid @Nonnull CentralPubkeyLockedMsg centralPubkeyLockedMsg) {
        String centralPubkeyBase64 = nmsciProperties.getCentralPubkeyBase64();
        String centralPrikeyBase64 = nmsciProperties.getCentralPrikeyBase64();

        if (centralPubkeyLockedMsg.getMsgType() != MsgTypeEnum.CentralPubkeyLockedMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.CentralPubkeyLockedMsg.getValue());
        }

        if (centralPubkeyLockedMsgRepository.existsById(centralPubkeyLockedMsg.getId())) {
            throw new ConflictException("该中心公钥冻结信息id(" + centralPubkeyLockedMsg.getId() + ")已存在");
        }

        if (centralPubkeyLockedMsgRepository.existsByCentralPubkey(centralPubkeyLockedMsg.getCentralPubkey())) {
            throw new ConflictException("该中心公钥(" + ByteArrayUtil.bytesToBase64(centralPubkeyLockedMsg.getCentralPubkey()) + ")已被冻结");
        }

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        if (!Arrays.equals(centralPubkeyLockedMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误，当前中心公钥为:(" + centralPubkeyBase64 + ")");
        }

        try {
            if (Secp256k1EncryptUtil.isNotLowS(centralPubkeyLockedMsg.getCentralSignaturePre())) {
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

        centralPubkeyLockedMsgPersistenceService.save(centralPubkeyLockedMsg);

        // 冻结信息一旦确认将进行原中心秘钥最后一次区块生成过程，将所有未装块的信息装块
        blockChainService.generateBlockUntilNoNotInBlockMsgs();

        logger.warn("中心公钥冻结成功，所有未装块的信息装块成功，程序终止");

        // 所有未装块的信息装块后终止程序
        System.exit(0);
    }
    public CentralPubkeyLockedMsg getCentralPubkeyLockedMsgById(UUID id) {
        return EntityLookup.requireById(id, "中心公钥冻结信息", centralPubkeyLockedMsgRepository::findById);
    }
    public CentralPubkeyLockedMsg getCentralPubkeyLockedMsgByCentralPubkey(byte[] centralPubkey) {
        return findCentralPubkeyLockedMsgByCentralPubkey(centralPubkey)
                .orElseThrow(() -> new IllegalArgumentException("中心公钥(" + ByteArrayUtil.bytesToHex(centralPubkey) + ")未冻结"));
    }
    public Optional<CentralPubkeyLockedMsg> findCentralPubkeyLockedMsgByCentralPubkey(byte[] centralPubkey) {
        if (centralPubkey == null || centralPubkey.length != 33) {
            throw new IllegalArgumentException("中心公钥不能为空或长度不为33字节");
        }

        return Optional.ofNullable(centralPubkeyLockedMsgRepository.findByCentralPubkey(centralPubkey));
    }
    public Slice<CentralPubkeyLockedMsg> listCentralPubkeyLockedMsgs(Pageable pageable) {
        return centralPubkeyLockedMsgRepository.findAll(pageable);
    }
}
