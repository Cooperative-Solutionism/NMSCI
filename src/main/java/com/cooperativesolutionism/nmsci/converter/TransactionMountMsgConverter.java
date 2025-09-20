package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.Arrays;

public class TransactionMountMsgConverter {
    public static TransactionMountMsg fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != 269) {
            throw new IllegalArgumentException("Invalid byte array size, expected 269 bytes.");
        }
        // 【信息类型2字节(5)】+【uuid16字节】+【挂载的交易记录信息的uuid16字节】+【交易难度目标4字节】+【随机数4字节】+【挂载的交易信息的消费节点公钥33字节】+【挂载的流转节点公钥33字节】+【中心公钥33字节】
        // +【消费节点对信息(前8项数据)签名64字节】+【挂载的生产者账号对信息(*前8项数据，也是前8项，方便两者同时签名)签名64字节】
        TransactionMountMsg msg = new TransactionMountMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 0, 2)));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setMountedTransactionRecordId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 18, 34)));
        msg.setTransactionDifficultyTarget(ByteArrayUtil.bytesToInt(Arrays.copyOfRange(byteData, 34, 38)));
        msg.setNonce(ByteArrayUtil.bytesToInt(Arrays.copyOfRange(byteData, 38, 42)));
        msg.setConsumeNodePubkey(Arrays.copyOfRange(byteData, 42, 75));
        msg.setFlowNodePubkey(Arrays.copyOfRange(byteData, 75, 108));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 108, 141));
        msg.setConsumeNodeSignature(Arrays.copyOfRange(byteData, 141, 205));
        msg.setFlowNodeSignature(Arrays.copyOfRange(byteData, 205, 269));
        return msg;
    }
}
