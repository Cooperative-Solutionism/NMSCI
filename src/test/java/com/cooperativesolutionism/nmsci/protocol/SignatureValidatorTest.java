package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg() {
        return CentralPubkeyEmpowerMsgConverter.fromByteArray(
                messageBuilder.centralPubkeyEmpower(
                        UUID.fromString("55555555-5555-5555-5555-555555555555"),
                        TestKeyPairs.FLOW_NODE_A,
                        TestKeyPairs.CENTRAL
                )
        );
    }
}
