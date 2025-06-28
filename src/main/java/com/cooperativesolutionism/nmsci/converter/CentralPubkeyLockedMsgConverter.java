package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.Arrays;

public class CentralPubkeyLockedMsgConverter {
    public static CentralPubkeyLockedMsg fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != 115) {
            throw new IllegalArgumentException("Invalid byte array size, expected 115 bytes.");
        }
        // 【信息类型2字节(1)】+【uuid16字节】+【中心公钥33字节】+【中心对信息(前3项数据)签名64字节】
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(byteData, 0, 2));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 18, 51));
        msg.setCentralSignaturePre(Arrays.copyOfRange(byteData, 51, 115));
        return msg;
    }
}
