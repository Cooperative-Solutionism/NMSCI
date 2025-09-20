package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.*;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
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
import java.util.List;
import java.util.UUID;

@Service
@Validated
public class TransactionRecordMsgServiceImpl implements TransactionRecordMsgService {

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Override
    public TransactionRecordMsg saveTransactionRecordMsg(@Valid @Nonnull TransactionRecordMsg transactionRecordMsg) {
        if (transactionRecordMsg.getMsgType() != MsgTypeEnum.TransactionRecordMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.TransactionRecordMsg.getValue());
        }

        if (transactionRecordMsgRepository.existsById(transactionRecordMsg.getId())) {
            throw new IllegalArgumentException("该交易记录信息id(" + transactionRecordMsg.getId() + ")已存在");
        }

        if (!CurrencyTypeEnum.containsValue(transactionRecordMsg.getCurrencyType())) {
            throw new IllegalArgumentException("货币类型错误，必须为以下数值:\n" + CurrencyTypeEnum.getAllEnumDescriptions());
        }

        BlockInfo newestBlockInfo = blockInfoRepository.findTopByOrderByHeightDesc();
        int transactionDifficultyTargetNbits = newestBlockInfo.getTransactionDifficultyTarget();
        if (!transactionRecordMsg.getTransactionDifficultyTarget().equals(transactionDifficultyTargetNbits)) {
            throw new IllegalArgumentException("交易难度目标与前区块中的交易难度目标不一致");
        }

        // 验证流转节点公钥是否已注册
        String flowNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(transactionRecordMsg.getFlowNodePubkey());
        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(transactionRecordMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未注册");
        }

        // 验证流转节点公钥是否已授权
        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        long centralPubkeyEmpowerMsgCount = centralPubkeyEmpowerMsgRepository.countByFlowNodePubkeyAndCentralPubkey(
                transactionRecordMsg.getFlowNodePubkey(),
                centralPubkey
        );
        if (centralPubkeyEmpowerMsgCount == 0) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未授权");
        }

        // 验证流转节点公钥是否已冻结
        if (flowNodeLockedMsgRepository.existsByFlowNodePubkey(transactionRecordMsg.getFlowNodePubkey())) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")已冻结");
        }

        if (centralPubkeyLockedMsgRepository.existsByCentralPubkey(transactionRecordMsg.getCentralPubkey())) {
            throw new IllegalArgumentException("该中心公钥(" + ByteArrayUtil.bytesToBase64(transactionRecordMsg.getCentralPubkey()) + ")已被冻结");
        }

        if (!Arrays.equals(transactionRecordMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误，当前中心公钥为:(" + centralPubkeyBase64 + ")");
        }

        try {
            if (Secp256k1EncryptUtil.isNotLowS(transactionRecordMsg.getConsumeNodeSignature())) {
                throw new IllegalArgumentException("消费节点签名不符合低S值要求");
            }
            if (Secp256k1EncryptUtil.isNotLowS(transactionRecordMsg.getFlowNodeSignature())) {
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
            byte[] centralPrikey = ByteArrayUtil.base64ToBytes(centralPrikeyBase64);
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey)));
            byte[] rawBytes = ArrayUtils.addAll(
                    centralSignData,
                    centralSignature
            );
            transactionRecordMsg.setConfirmTimestamp(timestamp);
            transactionRecordMsg.setCentralSignature(centralSignature);
            transactionRecordMsg.setRawBytes(rawBytes);
            transactionRecordMsg.setTxid(MerkleTreeUtil.calcTxid(rawBytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        msgAbstractService.saveMsgAbstract(transactionRecordMsg);

        return transactionRecordMsgRepository.save(transactionRecordMsg);
    }

    @Override
    public TransactionRecordMsg getTransactionRecordMsgById(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("交易记录信息id不能为空");
        }

        return transactionRecordMsgRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("交易记录信息id(" + id + ")不存在"));
    }

    @Override
    public List<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkey(byte[] consumeNodePubkey) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != 33) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByConsumeNodePubkey(consumeNodePubkey);
    }

    @Override
    public List<TransactionRecordMsg> getTransactionRecordMsgByFlowNodePubkey(byte[] flowNodePubkey) {
        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByFlowNodePubkey(flowNodePubkey);
    }

    @Override
    public List<TransactionRecordMsg> getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
            byte[] consumeNodePubkey,
            byte[] flowNodePubkey
    ) {
        if (consumeNodePubkey == null || consumeNodePubkey.length != 33) {
            throw new IllegalArgumentException("消费节点公钥不能为空或长度不正确");
        }

        if (flowNodePubkey == null || flowNodePubkey.length != 33) {
            throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
        }

        return transactionRecordMsgRepository.findByConsumeNodePubkeyAndFlowNodePubkey(consumeNodePubkey, flowNodePubkey);
    }

}
