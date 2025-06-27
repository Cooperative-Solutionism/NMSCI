package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.Arrays;
import java.util.UUID;

public class CentralPubkeyEmpowerMsgConverter {

    public static CentralPubkeyEmpowerMsg fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != 148) {
            throw new IllegalArgumentException("Invalid byte array size, expected 220 bytes.");
        }
        CentralPubkeyEmpowerMsg msg = new CentralPubkeyEmpowerMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(byteData, 0, 2));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setFlowNodePubkey(Arrays.copyOfRange(byteData, 18, 51));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 51, 84));
        msg.setFlowNodeSignature(Arrays.copyOfRange(byteData, 84, 148));
        return msg;
    }
}
