package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ProtocolRawBytesBuilderTest {

    private final ProtocolMessageBuilder messageBuilder = new ProtocolMessageBuilder();
    private final ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();

    @Test
    void buildsTransactionRecordVerifyDataInProtocolFieldOrder() {
        byte[] messageBytes = messageBuilder.transactionRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                100L,
                TestKeyPairs.CONSUME_NODE_A,
                TestKeyPairs.FLOW_NODE_A,
                TestKeyPairs.CENTRAL,
                0x20ffffff
        );

        assertArrayEquals(
                Arrays.copyOfRange(messageBytes, 0, 135),
                rawBytesBuilder.transactionRecordVerifyData(new TransactionRecordMsgConverter().fromByteArray(messageBytes))
        );
    }

    @Test
    void buildsTransactionMountVerifyDataInProtocolFieldOrder() {
        byte[] messageBytes = messageBuilder.transactionMount(
                UUID.fromString("12121212-1212-1212-1212-121212121212"),
                UUID.fromString("34343434-3434-3434-3434-343434343434"),
                TestKeyPairs.CONSUME_NODE_A,
                TestKeyPairs.FLOW_NODE_A,
                TestKeyPairs.CENTRAL,
                0x20ffffff
        );

        assertArrayEquals(
                Arrays.copyOfRange(messageBytes, 0, 141),
                rawBytesBuilder.transactionMountVerifyData(new TransactionMountMsgConverter().fromByteArray(messageBytes))
        );
    }

    @Test
    void buildsCentralPubkeyEmpowerVerifyDataInProtocolFieldOrder() {
        byte[] messageBytes = messageBuilder.centralPubkeyEmpower(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                TestKeyPairs.FLOW_NODE_A,
                TestKeyPairs.CENTRAL
        );

        assertArrayEquals(
                Arrays.copyOfRange(messageBytes, 0, 84),
                rawBytesBuilder.centralPubkeyEmpowerVerifyData(new CentralPubkeyEmpowerMsgConverter().fromByteArray(messageBytes))
        );
    }

    @Test
    void buildsCentralSignDataByAppendingSignaturesThenTimestamp() {
        byte[] messageBytes = messageBuilder.centralPubkeyEmpower(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                TestKeyPairs.FLOW_NODE_A,
                TestKeyPairs.CENTRAL
        );
        long timestamp = 123456789L;

        byte[] centralSignData = rawBytesBuilder.centralSignData(
                Arrays.copyOfRange(messageBytes, 0, 84),
                timestamp,
                Arrays.copyOfRange(messageBytes, 84, 148)
        );

        assertArrayEquals(
                ArrayUtils.addAll(messageBytes, ByteArrayUtil.longToBytes(timestamp)),
                centralSignData
        );
    }
}
