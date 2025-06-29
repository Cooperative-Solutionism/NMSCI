package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
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
import java.util.Base64;

@Service
@Validated
public class TransactionRecordMsgServiceImpl implements TransactionRecordMsgService {
    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Value("${transaction-difficulty-target-nbits}")
    private int transactionDifficultyTargetNbits;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Override
    public TransactionRecordMsg saveTransactionRecordMsg(@Valid @Nonnull TransactionRecordMsg transactionRecordMsg) {
        if (transactionRecordMsg.getMsgType() != 4) {
            throw new IllegalArgumentException("信息类型错误，必须为4");
        }

        if (transactionRecordMsgRepository.existsById(transactionRecordMsg.getId())) {
            throw new IllegalArgumentException("该交易记录信息id(" + transactionRecordMsg.getId() + ")已存在");
        }

        if (!CurrencyTypeEnum.containsValue(transactionRecordMsg.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        if (!transactionRecordMsg.getTransactionDifficultyTarget().equals(transactionDifficultyTargetNbits)) {
            throw new IllegalArgumentException("交易难度目标与前区块中的交易难度目标不一致");
        }

        // 验证流转节点公钥是否已注册
        String flowNodePubkeyBase64 = Base64.getEncoder().encodeToString(transactionRecordMsg.getFlowNodePubkey());
        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(transactionRecordMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未注册");
        }

        // 验证流转节点公钥是否已授权
        if (!centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(transactionRecordMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未授权");
        }

        // 验证流转节点公钥是否已冻结
        if (flowNodeLockedMsgRepository.existsByFlowNodePubkey(transactionRecordMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")已冻结");
        }

        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
        if (!Arrays.equals(transactionRecordMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误");
        }

        try {
            if (!Secp256k1EncryptUtil.isLowS(transactionRecordMsg.getConsumeNodeSignature())) {
                throw new IllegalArgumentException("消费节点签名不符合低S值要求");
            }
            if (!Secp256k1EncryptUtil.isLowS(transactionRecordMsg.getFlowNodeSignature())) {
                throw new IllegalArgumentException("流转节点签名不符合低S值要求");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 拼接验证数据 【信息类型2字节(4)】+【uuid16字节】+【金额8字节】+【货币类型2字节】+【交易难度目标4字节】+【随机数4字节】+【消费节点公钥33字节】+【流转节点公钥33字节】+【中心公钥33字节】
        byte[] verifyData;
        verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(transactionRecordMsg.getMsgType()),
                ByteArrayUtil.uuidToBytes(transactionRecordMsg.getId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.longToBytes(transactionRecordMsg.getAmount())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.shortToBytes(transactionRecordMsg.getCurrencyType())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.intToBytes(transactionRecordMsg.getTransactionDifficultyTarget())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.intToBytes(transactionRecordMsg.getNonce())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                transactionRecordMsg.getConsumeNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                transactionRecordMsg.getFlowNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                transactionRecordMsg.getCentralPubkey()
        );

        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
        BigInteger verifyDataHash = new BigInteger(1, Sha256Util.doubleDigest(verifyData));
        if (verifyDataHash.compareTo(transactionDifficultyTarget) > 0) {
            throw new IllegalArgumentException("前9项数据的hash值不符合注册难度目标要求");
        }

        try {
            boolean isValidConsumeNodeSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    transactionRecordMsg.getConsumeNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(transactionRecordMsg.getConsumeNodePubkey())
            );
            if (!isValidConsumeNodeSignature) {
                throw new IllegalArgumentException("消费节点签名验证失败");
            }

            boolean isValidFlowNodeSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    transactionRecordMsg.getFlowNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(transactionRecordMsg.getFlowNodePubkey())
            );
            if (!isValidFlowNodeSignature) {
                throw new IllegalArgumentException("流转节点签名验证失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long timestamp = DateUtil.getCurrentMicros();

        // 拼接中心签名数据 【信息类型2字节(4)】+【uuid16字节】+【金额8字节】+【货币类型2字节】+【交易难度目标4字节】+【随机数4字节】+【消费节点公钥33字节】+【流转节点公钥33字节】+【中心公钥33字节】
        // +【消费节点对信息(前9项数据)签名64字节】+【流转节点对信息(*前9项数据，也是前9项，方便两者同时签名)签名64字节】+【时间戳8字节】
        byte[] centralSignData;
        centralSignData = ArrayUtils.addAll(
                verifyData,
                transactionRecordMsg.getConsumeNodeSignature()
        );
        centralSignData = ArrayUtils.addAll(
                centralSignData,
                transactionRecordMsg.getFlowNodeSignature()
        );
        centralSignData = ArrayUtils.addAll(
                centralSignData,
                ByteArrayUtil.longToBytes(timestamp)
        );

        try {
            byte[] centralPrikey = Base64.getDecoder().decode(centralPrikeyBase64);
            byte[] centralSignature = Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey));
            transactionRecordMsg.setConfirmTimestamp(timestamp);
            transactionRecordMsg.setCentralSignature(centralSignature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return transactionRecordMsgRepository.save(transactionRecordMsg);
    }
}
