package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.*;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
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
public class TransactionMountMsgServiceImpl implements TransactionMountMsgService {
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

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Override
    public TransactionMountMsg saveTransactionMountMsg(@Valid @Nonnull TransactionMountMsg transactionMountMsg) {
        if (transactionMountMsg.getMsgType() != 5) {
            throw new IllegalArgumentException("信息类型错误，必须为5");
        }

        if (transactionMountMsgRepository.existsById(transactionMountMsg.getId())) {
            throw new IllegalArgumentException("该交易挂载信息id(" + transactionMountMsg.getId() + ")已存在");
        }

        if (!transactionRecordMsgRepository.existsById(transactionMountMsg.getMountedTransactionRecordId())) {
            throw new IllegalArgumentException("挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")不存在");
        }

        if (!transactionMountMsg.getTransactionDifficultyTarget().equals(transactionDifficultyTargetNbits)) {
            throw new IllegalArgumentException("交易难度目标与前区块中的交易难度目标不一致");
        }

        // 消费节点公钥与要挂载的交易信息中的消费节点公钥需相同
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgRepository.findById(transactionMountMsg.getMountedTransactionRecordId())
                .orElseThrow(() -> new IllegalArgumentException("挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")不存在"));
        byte[] consumeNodePubkey = transactionRecordMsg.getConsumeNodePubkey();
        if (!Arrays.equals(transactionMountMsg.getConsumeNodePubkey(), consumeNodePubkey)) {
            String consumeNodePubkeyBase64 = Base64.getEncoder().encodeToString(consumeNodePubkey);
            throw new IllegalArgumentException("挂载的交易记录信息中的消费节点公钥(" + consumeNodePubkeyBase64 + ")与当前交易挂载信息中的消费节点公钥不一致");
        }

        // 验证流转节点公钥是否已注册
        String flowNodePubkeyBase64 = Base64.getEncoder().encodeToString(transactionMountMsg.getFlowNodePubkey());
        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(transactionMountMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未注册");
        }

        // 验证流转节点公钥是否已授权
        if (!centralPubkeyEmpowerMsgRepository.existsByFlowNodePubkey(transactionMountMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未授权");
        }

        // 验证流转节点公钥是否已冻结
        if (flowNodeLockedMsgRepository.existsByFlowNodePubkey(transactionMountMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")已冻结");
        }

        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
        if (!Arrays.equals(transactionMountMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误");
        }

        try {
            if (!Secp256k1EncryptUtil.isLowS(transactionMountMsg.getConsumeNodeSignature())) {
                throw new IllegalArgumentException("消费节点签名不符合低S值要求");
            }
            if (!Secp256k1EncryptUtil.isLowS(transactionMountMsg.getFlowNodeSignature())) {
                throw new IllegalArgumentException("流转节点签名不符合低S值要求");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 拼接验证数据 【信息类型2字节(5)】+【uuid16字节】+【挂载的交易记录信息的uuid16字节】+【交易难度目标4字节】+【随机数4字节】+【挂载的交易信息的消费节点公钥33字节】+【挂载的流转节点公钥33字节】+【中心公钥33字节】
        byte[] verifyData;
        verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(transactionMountMsg.getMsgType()),
                ByteArrayUtil.uuidToBytes(transactionMountMsg.getId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.uuidToBytes(transactionMountMsg.getMountedTransactionRecordId())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.intToBytes(transactionMountMsg.getTransactionDifficultyTarget())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                ByteArrayUtil.intToBytes(transactionMountMsg.getNonce())
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                transactionMountMsg.getConsumeNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                transactionMountMsg.getFlowNodePubkey()
        );
        verifyData = ArrayUtils.addAll(
                verifyData,
                transactionMountMsg.getCentralPubkey()
        );

        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
        BigInteger verifyDataHash = new BigInteger(1, Sha256Util.doubleDigest(verifyData));
        if (verifyDataHash.compareTo(transactionDifficultyTarget) > 0) {
            throw new IllegalArgumentException("前8项数据的hash值不符合注册难度目标要求");
        }

        try {
            boolean isValidConsumeNodeSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    transactionMountMsg.getConsumeNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(transactionMountMsg.getConsumeNodePubkey())
            );
            if (!isValidConsumeNodeSignature) {
                throw new IllegalArgumentException("消费节点签名验证失败");
            }

            boolean isValidFlowNodeSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    transactionMountMsg.getFlowNodeSignature(),
                    Secp256k1EncryptUtil.compressedToPublicKey(transactionMountMsg.getFlowNodePubkey())
            );
            if (!isValidFlowNodeSignature) {
                throw new IllegalArgumentException("流转节点签名验证失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long timestamp = DateUtil.getCurrentMicros();

        // 拼接中心签名数据 【信息类型2字节(5)】+【uuid16字节】+【挂载的交易记录信息的uuid16字节】+【交易难度目标4字节】+【随机数4字节】+【挂载的交易信息的消费节点公钥33字节】+【挂载的流转节点公钥33字节】+【中心公钥33字节】
        // +【消费节点对信息(前8项数据)签名64字节】+【挂载的生产者账号对信息(*前8项数据，也是前8项，方便两者同时签名)签名64字节】+【时间戳8字节】
        byte[] centralSignData;
        centralSignData = ArrayUtils.addAll(
                verifyData,
                transactionMountMsg.getConsumeNodeSignature()
        );
        centralSignData = ArrayUtils.addAll(
                centralSignData,
                transactionMountMsg.getFlowNodeSignature()
        );
        centralSignData = ArrayUtils.addAll(
                centralSignData,
                ByteArrayUtil.longToBytes(timestamp)
        );

        try {
            byte[] centralPrikey = Base64.getDecoder().decode(centralPrikeyBase64);
            byte[] centralSignature = Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey));
            transactionMountMsg.setConfirmTimestamp(timestamp);
            transactionMountMsg.setCentralSignature(centralSignature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return transactionMountMsgRepository.save(transactionMountMsg);
    }
}
