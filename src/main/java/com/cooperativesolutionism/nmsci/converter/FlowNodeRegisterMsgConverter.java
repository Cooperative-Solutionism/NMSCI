package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.Arrays;

public class FlowNodeRegisterMsgConverter {
    public static FlowNodeRegisterMsg fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != 123) {
            throw new IllegalArgumentException("Invalid byte array size, expected 123 bytes.");
        }

        // 【信息类型2字节(0)】+【uuid16字节】+【注册难度目标4字节】+【随机数4字节】+【流转节点公钥33字节】+【流转节点对信息(前5项数据)签名64字节】
        FlowNodeRegisterMsg msg = new FlowNodeRegisterMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 0, 2)));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setRegisterDifficultyTarget(ByteArrayUtil.bytesToInt(Arrays.copyOfRange(byteData, 18, 22)));
        msg.setNonce(ByteArrayUtil.bytesToInt(Arrays.copyOfRange(byteData, 22, 26)));
        msg.setFlowNodePubkey(Arrays.copyOfRange(byteData, 26, 59));
        msg.setFlowNodeSignature(Arrays.copyOfRange(byteData, 59, 123));
        return msg;
    }
}
