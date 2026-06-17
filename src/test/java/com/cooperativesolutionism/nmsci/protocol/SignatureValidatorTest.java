package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class SignatureValidatorTest {

    private final ProtocolMessageBuilder messageBuilder = new ProtocolMessageBuilder();
    private final ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
    private final SignatureValidator signatureValidator = new SignatureValidator();

    @Test
    void acceptsValidSecp256k1Signature() {
        CentralPubkeyEmpowerMsg msg = centralPubkeyEmpowerMsg();

        signatureValidator.validateSignature(
                rawBytesBuilder.centralPubkeyEmpowerVerifyData(msg),
                msg.getFlowNodeSignature(),
                msg.getFlowNodePubkey(),
                "signature failed"
        );
    }

    @Test
    void rejectsInvalidSecp256k1Signature() {
        CentralPubkeyEmpowerMsg msg = centralPubkeyEmpowerMsg();
        msg.getFlowNodeSignature()[0] = (byte) (msg.getFlowNodeSignature()[0] ^ 0x01);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> signatureValidator.validateSignature(
                        rawBytesBuilder.centralPubkeyEmpowerVerifyData(msg),
                        msg.getFlowNodeSignature(),
                        msg.getFlowNodePubkey(),
                        "signature failed"
                )
        );

        assertEquals("signature failed", exception.getMessage());
    }

    @Test
    void rejectsLowSValidationWhenSignatureIsNotRs64Bytes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> signatureValidator.validateLowS(new byte[63], "签名不符合低S标准")
        );

        assertEquals("签名必须为64字节RS格式", exception.getMessage());
    }

    @Test
    void mapsLowSDecodeFailureToBadRequest() {
        try (MockedStatic<Secp256k1EncryptUtil> mocked = mockStatic(Secp256k1EncryptUtil.class)) {
            mocked.when(() -> Secp256k1EncryptUtil.isNotLowS(any())).thenThrow(new IOException("decode failed"));

            BadRequestException exception = assertThrows(
                    BadRequestException.class,
                    () -> signatureValidator.validateLowS(new byte[64], "中心预签名不符合低S标准")
            );

            assertEquals("中心预签名不符合低S标准", exception.getMessage());
        }
    }

    @Test
    void mapsSignatureDecodeFailureToBadRequest() {
        try (MockedStatic<Secp256k1EncryptUtil> mocked = mockStatic(Secp256k1EncryptUtil.class)) {
            mocked.when(() -> Secp256k1EncryptUtil.verifySignature(any(), any(), any()))
                    .thenThrow(new IllegalStateException("point decode failed"));

            BadRequestException exception = assertThrows(
                    BadRequestException.class,
                    () -> signatureValidator.validateSignature(new byte[1], new byte[64], new byte[33], "签名验证失败")
            );

            assertEquals("签名验证失败", exception.getMessage());
        }
    }

    private CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg() {
        return new CentralPubkeyEmpowerMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyEmpower(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        TestKeyPairs.FLOW_NODE_A,
                        TestKeyPairs.CENTRAL
                )
        );
    }
}
