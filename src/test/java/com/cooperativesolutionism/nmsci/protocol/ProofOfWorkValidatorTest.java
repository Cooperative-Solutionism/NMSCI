package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProofOfWorkValidatorTest {

    private final ProtocolMessageBuilder messageBuilder = new ProtocolMessageBuilder();
    private final ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
    private final ProofOfWorkValidator proofOfWorkValidator = new ProofOfWorkValidator();

    @Test
    void acceptsVerifyDataMeetingNbitsTarget() {
        byte[] messageBytes = messageBuilder.transactionRecord(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                100L,
                TestKeyPairs.CONSUME_NODE_A,
                TestKeyPairs.FLOW_NODE_A,
                TestKeyPairs.CENTRAL,
                0x20ffffff
        );

        proofOfWorkValidator.validate(
                rawBytesBuilder.transactionRecordVerifyData(TransactionRecordMsgConverter.fromByteArray(messageBytes)),
                0x20ffffff,
                "pow failed"
        );
    }

    @Test
    void rejectsVerifyDataAboveNbitsTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> proofOfWorkValidator.validate(new byte[]{1, 2, 3}, 0x03000001, "pow failed")
        );

        assertEquals("pow failed", exception.getMessage());
    }
}
