package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.Arrays;

public class TransactionRecordMsgConverter {
    public static TransactionRecordMsg fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != 263) {
            throw new IllegalArgumentException("Invalid byte array size, expected 263 bytes.");
        }

        // 【信息类型2字节(4)】+【uuid16字节】+【金额8字节】+【货币类型2字节】+【交易难度目标4字节】+【随机数4字节】+【消费节点公钥33字节】+【流转节点公钥33字节】+【中心公钥33字节】
        // +【消费节点对信息(前9项数据)签名64字节】+【流转节点对信息(*前9项数据，也是前9项，方便两者同时签名)签名64字节】
        TransactionRecordMsg msg = new TransactionRecordMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 0, 2)));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setAmount(ByteArrayUtil.bytesToLong(Arrays.copyOfRange(byteData, 18, 26)));
        msg.setCurrencyType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 26, 28)));
        msg.setTransactionDifficultyTarget(ByteArrayUtil.bytesToInt(Arrays.copyOfRange(byteData, 28, 32)));
        msg.setNonce(ByteArrayUtil.bytesToInt(Arrays.copyOfRange(byteData, 32, 36)));
        msg.setConsumeNodePubkey(Arrays.copyOfRange(byteData, 36, 69));
        msg.setFlowNodePubkey(Arrays.copyOfRange(byteData, 69, 102));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 102, 135));
        msg.setConsumeNodeSignature(Arrays.copyOfRange(byteData, 135, 199));
        msg.setFlowNodeSignature(Arrays.copyOfRange(byteData, 199, 263));
        return msg;
    }
}
