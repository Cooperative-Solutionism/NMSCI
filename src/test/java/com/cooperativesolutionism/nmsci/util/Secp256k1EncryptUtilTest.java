package com.cooperativesolutionism.nmsci.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

@Disabled
class Secp256k1EncryptUtilTest {

    @Test
    void generateKeyPair() {
        Security.addProvider(new BouncyCastleProvider());
        try {
            KeyPair keyPair = Secp256k1EncryptUtil.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();
            byte[] publicKeyBytes = Secp256k1EncryptUtil.publicKeyToCompressed(publicKey);
            byte[] privateKeyBytes = Secp256k1EncryptUtil.privateKeyToRaw(privateKey);
            String publicKeyBase64 = ByteArrayUtil.bytesToBase64(publicKeyBytes);
            String privateKeyBase64 = ByteArrayUtil.bytesToBase64(privateKeyBytes);
            System.out.println("Public Key: " + keyPair.getPublic().toString());
            System.out.println("Private Key: " + keyPair.getPrivate().toString());
            System.out.println("Public Key Bytes: " + Arrays.toString(publicKeyBytes));
            System.out.println("Private Key Bytes: " + Arrays.toString(privateKeyBytes));
            System.out.println("Public Key Base64: " + publicKeyBase64);
            System.out.println("Private Key Base64: " + privateKeyBase64);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void derToRsTest() {
        try {
            byte[] der = new byte[]{48, 69, 2, 32, 126, -46, -115, -87, 59, -6, -93, -13, -107, 50, 23, -3, -41, 112, 32, -86, 1, 60, -68, 92, 51, 111, -38, -62, 91, 124, -21, -46, 34, 62, -34, -93, 2, 33, 0, -37, 45, 109, 94, 4, 9, -96, -79, 101, -127, 27, 15, -13, -15, -57, 57, -127, -62, 97, -66, 14, 41, -90, -67, 88, 88, -11, -120, 34, -6, 26, -41};
            System.out.println("der = " + ByteArrayUtil.bytesToHex(der));
            byte[] rs = Secp256k1EncryptUtil.derToRs(der);
            System.out.println("rs = " + ByteArrayUtil.bytesToHex(rs));
            byte[] der2 = Secp256k1EncryptUtil.rsToDer(rs);
            System.out.println("der2 = " + ByteArrayUtil.bytesToHex(der2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}