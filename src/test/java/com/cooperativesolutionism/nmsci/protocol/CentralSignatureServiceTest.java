package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentralSignatureServiceTest {

    private final ProtocolMessageBuilder messageBuilder = new ProtocolMessageBuilder();
    private final ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
    private final CentralSignatureService centralSignatureService = new CentralSignatureService(properties(), rawBytesBuilder);

    @Test
    void signsRawBytesAndPopulatesMessageFields() throws Exception {
        CentralPubkeyEmpowerMsg msg = new CentralPubkeyEmpowerMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyEmpower(
                        UUID.fromString("66666666-6666-6666-6666-666666666666"),
                        TestKeyPairs.FLOW_NODE_A,
                        TestKeyPairs.CENTRAL
                )
        );
        byte[] verifyData = rawBytesBuilder.centralPubkeyEmpowerVerifyData(msg);
        long timestamp = 123456789L;

        centralSignatureService.signAndPopulate(msg, verifyData, timestamp, msg.getFlowNodeSignature());

        assertEquals(timestamp, msg.getConfirmTimestamp());
        assertEquals(64, msg.getCentralSignature().length);
        assertEquals(32, msg.getTxid().length);
        assertEquals(verifyData.length + msg.getFlowNodeSignature().length + Long.BYTES + 64, msg.getRawBytes().length);
        assertTrue(Secp256k1EncryptUtil.verifySignature(
                rawBytesBuilder.centralSignData(verifyData, timestamp, msg.getFlowNodeSignature()),
                msg.getCentralSignature(),
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.CENTRAL.pubkey())
        ));
    }

    @Test
    void signsCentralPubkeyLockedRawBytesInProtocolOrder() throws Exception {
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyLocked(
                        UUID.fromString("77777777-7777-7777-7777-777777777777"),
                        TestKeyPairs.CENTRAL
                )
        );
        byte[] verifyData = rawBytesBuilder.centralPubkeyLockedVerifyData(msg);
        long timestamp = 987654321L;

        centralSignatureService.signAndPopulate(msg, verifyData, timestamp, msg.getCentralSignaturePre());

        assertEquals(timestamp, msg.getConfirmTimestamp());
        assertEquals(64, msg.getCentralSignature().length);
        assertEquals(32, msg.getTxid().length);
        assertEquals(verifyData.length + msg.getCentralSignaturePre().length + Long.BYTES + 64, msg.getRawBytes().length);
        assertArrayEquals(
                ByteBuffer.allocate(verifyData.length + msg.getCentralSignaturePre().length + Long.BYTES + msg.getCentralSignature().length)
                        .put(verifyData)
                        .put(msg.getCentralSignaturePre())
                        .putLong(timestamp)
                        .put(msg.getCentralSignature())
                        .array(),
                msg.getRawBytes()
        );
        assertTrue(Secp256k1EncryptUtil.verifySignature(
                rawBytesBuilder.centralSignData(verifyData, timestamp, msg.getCentralSignaturePre()),
                msg.getCentralSignature(),
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.CENTRAL.pubkey())
        ));
    }

    private NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }
}
