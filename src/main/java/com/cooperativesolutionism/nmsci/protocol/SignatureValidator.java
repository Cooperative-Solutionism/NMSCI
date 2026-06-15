package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SignatureValidator {

    public void validateLowS(byte[] signature, String errorMessage) {
        if (signature == null || signature.length != 64) {
            throw new IllegalArgumentException("签名必须为64字节RS格式");
        }
        try {
            if (Secp256k1EncryptUtil.isNotLowS(signature)) {
                throw new IllegalArgumentException(errorMessage);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void validateSignature(byte[] verifyData, byte[] signature, byte[] compressedPubkey, String errorMessage) {
        try {
            boolean isValidSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    signature,
                    Secp256k1EncryptUtil.compressedToPublicKey(compressedPubkey)
            );
            if (!isValidSignature) {
                throw new IllegalArgumentException(errorMessage);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
