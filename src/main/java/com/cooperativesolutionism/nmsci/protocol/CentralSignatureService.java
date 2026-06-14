package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.model.CentrallySignedMessage;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.bitcoinj.crypto.ECKey;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Service
public class CentralSignatureService {

    private final NmsciProperties nmsciProperties;
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder;

    // 中心签名密钥来自固定配置，惰性构造一次并复用，避免每次签名都由私钥重新派生公钥点。
    private volatile ECKey centralSigningKey;

    public CentralSignatureService(NmsciProperties nmsciProperties, ProtocolRawBytesBuilder protocolRawBytesBuilder) {
        this.nmsciProperties = nmsciProperties;
        this.protocolRawBytesBuilder = protocolRawBytesBuilder;
    }

    private ECKey centralSigningKey() {
        ECKey key = centralSigningKey;
        if (key == null) {
            synchronized (this) {
                key = centralSigningKey;
                if (key == null) {
                    key = Secp256k1EncryptUtil.rawToECKey(ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPrikeyBase64()));
                    centralSigningKey = key;
                }
            }
        }
        return key;
    }

    public void signAndPopulate(CentrallySignedMessage message, byte[] verifyData, byte[]... signatures) {
        signAndPopulate(message, verifyData, DateUtil.getCurrentMicros(), signatures);
    }

    void signAndPopulate(CentrallySignedMessage message, byte[] verifyData, long timestamp, byte[]... signatures) {
        byte[] centralSignData = protocolRawBytesBuilder.centralSignData(verifyData, timestamp, signatures);
        try {
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(
                    Secp256k1EncryptUtil.signData(centralSignData, centralSigningKey())
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
