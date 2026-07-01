package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;

@Component
public class CentralPubkeyLockedMsgConverter extends AbstractMessageConverter<CentralPubkeyLockedMsg> {

    @Override
    public MsgTypeEnum msgType() {
        return MsgTypeEnum.CentralPubkeyLockedMsg;
    }

    @Override
    public int expectedSize() {
        return CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;
    }

    @Override
    protected CentralPubkeyLockedMsg decode(byte[] byteData) {
        // 【信息类型2字节(2)】+【uuid16字节】+【中心公钥33字节】+【中心对信息(前3项数据)签名64字节】
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsg();
        msg.setMsgType(ByteArrayUtil.bytesToShort(Arrays.copyOfRange(byteData, 0, 2)));
        msg.setId(ByteArrayUtil.bytesToUUID(Arrays.copyOfRange(byteData, 2, 18)));
        msg.setCentralPubkey(Arrays.copyOfRange(byteData, 18, 51));
        msg.setCentralSignaturePre(Arrays.copyOfRange(byteData, 51, 115));
        return msg;
    }
}
