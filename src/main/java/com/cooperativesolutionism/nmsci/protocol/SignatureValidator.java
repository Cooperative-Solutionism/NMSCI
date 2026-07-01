package com.cooperativesolutionism.nmsci.protocol;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SignatureValidator {

    public void validateLowS(byte[] signature, String errorMessage) {
        if (signature == null || signature.length != RS_SIGNATURE_BYTES) {
            throw new IllegalArgumentException("签名必须为64字节RS格式");
        }
        try {
            if (Secp256k1EncryptUtil.isNotLowS(signature)) {
                throw new IllegalArgumentException(errorMessage);
            }
        } catch (IOException e) {
            // 解析客户端提交的签名字节失败属客户端输入错误，映射为 400 而非 500
            throw new BadRequestException(errorMessage, e);
        }
    }

    public void validateSignature(byte[] verifyData, byte[] signature, byte[] compressedPubkey, String errorMessage) {
        try {
            // 性能审计 H3：直接以压缩公钥字节验签，避免每条签名都做一次 KeyFactory/PublicKey 往返。
            boolean isValidSignature = Secp256k1EncryptUtil.verifySignature(
                    verifyData,
                    signature,
                    compressedPubkey
            );
            if (!isValidSignature) {
                throw new IllegalArgumentException(errorMessage);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // DER 解析、曲线点解码等失败均源于客户端提交的签名/公钥字节，映射为 400 而非 500
            throw new BadRequestException(errorMessage, e);
        }
    }
}
