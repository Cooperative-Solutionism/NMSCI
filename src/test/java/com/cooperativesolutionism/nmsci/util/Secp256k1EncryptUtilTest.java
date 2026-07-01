package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.Security;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Secp256k1EncryptUtilTest {

    private static final BigInteger CURVE_ORDER = SECNamedCurves.getByName("secp256k1").getN();
    private static final BigInteger FIELD_PRIME = new BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",
            16
    );

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

    @Test
    void verifySignatureRejectsWrongPublicKey() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("01020304");
        byte[] signature = signRs(data, TestKeyPairs.FLOW_NODE_A.prikey());

        assertFalse(Secp256k1EncryptUtil.verifySignature(
                data,
                signature,
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.FLOW_NODE_B.pubkey())
        ));
    }

    @Test
    void verifySignatureRejectsTamperedData() throws Exception {
        byte[] data = ByteArrayUtil.hexToBytes("01020304");
        byte[] signature = signRs(data, TestKeyPairs.FLOW_NODE_A.prikey());
        byte[] tamperedData = ByteArrayUtil.hexToBytes("01020305");

        assertFalse(Secp256k1EncryptUtil.verifySignature(
                tamperedData,
                signature,
                Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.FLOW_NODE_A.pubkey())
        ));
    }

    @Test
    void rsToDerRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rsToDer(null));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rsToDer(new byte[63]));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rsToDer(new byte[65]));
    }

    @Test
    void isNotLowSDetectsHighS() throws Exception {
        byte[] highS = rsWithS(CURVE_ORDER.shiftRight(1).add(BigInteger.ONE));

        assertTrue(Secp256k1EncryptUtil.isNotLowS(highS));
    }

    @Test
    void isNotLowSRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.isNotLowS(null));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.isNotLowS(new byte[63]));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.isNotLowS(new byte[65]));
    }

    @Test
    void derToRsRejectsMalformedDer() throws Exception {
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(null));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(new byte[]{0x01, 0x02}));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(new byte[]{0x30, 0x03, 0x02, 0x01}));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(new ASN1Integer(BigInteger.ONE).getEncoded()));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSequence()));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSequence(
                new ASN1Integer(BigInteger.ONE),
                new ASN1Integer(BigInteger.TWO),
                new ASN1Integer(BigInteger.valueOf(3))
        )));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSequence(
                new DEROctetString(new byte[]{1}),
                new ASN1Integer(BigInteger.ONE)
        )));
    }

    @Test
    void derToRsRejectsInvalidScalars() {
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ZERO, BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, BigInteger.ZERO)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.valueOf(-1), BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, BigInteger.valueOf(-1))));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(CURVE_ORDER, BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, CURVE_ORDER)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(CURVE_ORDER.add(BigInteger.ONE), BigInteger.ONE)));
        assertThrows(IOException.class, () -> Secp256k1EncryptUtil.derToRs(derSignature(BigInteger.ONE, CURVE_ORDER.add(BigInteger.ONE))));
    }

    @Test
    void compressedToPublicKeyRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(null));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(new byte[32]));

        byte[] invalidPrefix = Arrays.copyOf(TestKeyPairs.FLOW_NODE_A.pubkey(), 33);
        invalidPrefix[0] = 0x04;
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(invalidPrefix));

        byte[] invalidPoint = new byte[33];
        invalidPoint[0] = 0x02;
        System.arraycopy(toFixed32(FIELD_PRIME), 0, invalidPoint, 1, 32);
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.compressedToPublicKey(invalidPoint));
    }

    @Test
    void rawPrivateKeyConversionRejectsInvalidInput() {
        assertRawPrivateKeyRejected(null);
        assertRawPrivateKeyRejected(new byte[31]);
        assertRawPrivateKeyRejected(new byte[33]);
        assertRawPrivateKeyRejected(new byte[32]);
        assertRawPrivateKeyRejected(toFixed32(CURVE_ORDER));
        assertRawPrivateKeyRejected(toFixed32(CURVE_ORDER.add(BigInteger.ONE)));
    }

    private static byte[] signRs(byte[] data, byte[] rawPrivateKey) throws Exception {
        return Secp256k1EncryptUtil.derToRs(
                Secp256k1EncryptUtil.signData(data, Secp256k1EncryptUtil.rawToPrivateKey(rawPrivateKey))
        );
    }

    private static byte[] derSignature(BigInteger r, BigInteger s) throws IOException {
        return derSequence(new ASN1Integer(r), new ASN1Integer(s));
    }

    private static byte[] derSequence(ASN1Primitive... values) throws IOException {
        return new DERSequence(values).getEncoded();
    }

    private static byte[] rsWithS(BigInteger s) {
        byte[] rs = new byte[64];
        byte[] sBytes = toFixed32(s);
        System.arraycopy(sBytes, 0, rs, 32, 32);
        return rs;
    }

    private static byte[] toFixed32(BigInteger value) {
        byte[] valueBytes = value.toByteArray();
        if (valueBytes.length > 32) {
            valueBytes = Arrays.copyOfRange(valueBytes, valueBytes.length - 32, valueBytes.length);
        }
        byte[] fixed = new byte[32];
        System.arraycopy(valueBytes, 0, fixed, 32 - valueBytes.length, valueBytes.length);
        return fixed;
    }

    private static void assertRawPrivateKeyRejected(byte[] rawPrivateKey) {
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rawToPrivateKey(rawPrivateKey));
        assertThrows(IllegalArgumentException.class, () -> Secp256k1EncryptUtil.rawToECKey(rawPrivateKey));
    }
}
