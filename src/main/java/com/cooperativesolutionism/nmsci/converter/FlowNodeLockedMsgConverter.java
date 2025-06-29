package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.Arrays;

public class FlowNodeLockedMsgConverter {
    public static FlowNodeLockedMsg fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != 148) {
            throw new IllegalArgumentException("Invalid byte array size, expected 148 bytes.");
        }

        // 【信息类型2字节(3)】+【uuid16字节】+【流转节点公钥33字节】+【中心公钥33字节】+【流转节点对信息(前4项数据)签名64字节】
        FlowNodeLockedMsg msg = new FlowNodeLockedMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 0, 2)));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setFlowNodePubkey(Arrays.copyOfRange(byteData, 18, 51));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 51, 84));
        msg.setFlowNodeSignature(Arrays.copyOfRange(byteData, 84, 148));
        return msg;
    }
}
