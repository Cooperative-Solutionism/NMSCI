package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.model.CentrallySignedMessage;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Service
public class CentralSignatureService {

    private final NmsciProperties nmsciProperties;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;

    public CentralSignatureService(NmsciProperties nmsciProperties, ProtocolRawBytesBuilder protocolRawBytesBuilder) {
        this.nmsciProperties = nmsciProperties;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
    }

    public void signAndPopulate(CentrallySignedMessage message, byte[] verifyData, byte[]... signatures) {
        signAndPopulate(message, verifyData, DateUtil.getCurrentMicros(), signatures);
    }

    void signAndPopulate(CentrallySignedMessage message, byte[] verifyData, long timestamp, byte[]... signatures) {
        byte[] centralSignData = protocolRawBytesBuilder.centralSignData(verifyData, timestamp, signatures);
        try {
            byte[] centralPrikey = ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPrikeyBase64());
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(
                    Secp256k1EncryptUtil.signData(centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey))
            );
            byte[] rawBytes = ByteBuffer.allocate(centralSignData.length + centralSignature.length)
                    .put(centralSignData)
                    .put(centralSignature)
                    .array();
            message.setConfirmTimestamp(timestamp);
            message.setCentralSignature(centralSignature);
            message.setRawBytes(rawBytes);
            message.setTxid(MerkleTreeUtil.calcTxid(rawBytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
