package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Secp256k1EncryptUtilTest {

    @BeforeAll
    static void addProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void signsAndVerifiesRsSignature() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("01020304");
        byte[] signature = Secp256k1EncryptUtil.derToRs(
                Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(TestKeyPairs.FLOW_NODE_A.prikey()))
        );

        assertFalse(Secp256k1EncryptUtil.isNotLowS(signature));
        assertTrue(Secp256k1EncryptUtil.verifySignature(
                data,
                signature,
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.FLOW_NODE_A.pubkey())
        ));
    }

    @Test
    void convertsRsSignatureToDerAndBack() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("05060708");
        byte[] rs = Secp256k1EncryptUtil.derToRs(
                Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(TestKeyPairs.CONSUME_NODE_A.prikey()))
        );

        assertArrayEquals(rs, Secp256k1EncryptUtil.derToRs(Secp256k1EncryptUtil.rsToDer(rs)));
    }
}
