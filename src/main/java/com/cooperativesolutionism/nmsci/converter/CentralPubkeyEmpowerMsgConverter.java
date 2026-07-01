package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;

@Component
public class CentralPubkeyEmpowerMsgConverter extends AbstractMessageConverter<CentralPubkeyEmpowerMsg> {

    @Override
    public MsgTypeEnum msgType() {
        return MsgTypeEnum.CentralPubkeyEmpowerMsg;
    }

    @Override
    public int expectedSize() {
        return CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;
    }

    @Override
    protected CentralPubkeyEmpowerMsg decode(byte[] byteData) {
        // 【信息类型2字节(1)】+【uuid16字节】+【流转节点公钥33字节】+【中心公钥33字节】+【流转节点对信息(前4项数据)签名64字节】
        CentralPubkeyEmpowerMsg msg = new CentralPubkeyEmpowerMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 0, 2)));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setFlowNodePubkey(Arrays.copyOfRange(byteData, 18, 51));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 51, 84));
        msg.setFlowNodeSignature(Arrays.copyOfRange(byteData, 84, 148));
        return msg;
    }
}
