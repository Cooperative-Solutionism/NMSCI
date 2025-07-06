package com.cooperativesolutionism.nmsci.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

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

}