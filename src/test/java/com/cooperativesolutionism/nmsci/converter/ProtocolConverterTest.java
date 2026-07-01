package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolConverterTest {

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();

    @Test
    void parsesFlowNodeRegisterMessageFields() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        byte[] bytes = builder.flowNodeRegister(id, TestKeyPairs.FLOW_NODE_A, 0x20ffffff);

        var msg = new FlowNodeRegisterMsgConverter().fromByteArray(bytes);

        assertEquals((short) 0, msg.getMsgType());
        assertEquals(id, msg.getId());
        assertEquals(0x20ffffff, msg.getRegisterDifficultyTarget());
        assertArrayEquals(TestKeyPairs.FLOW_NODE_A.pubkey(), msg.getFlowNodePubkey());
        assertEquals(64, msg.getFlowNodeSignature().length);
    }

    @Test
    void parsesTransactionRecordMessageFields() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        byte[] bytes = builder.transactionRecord(id, 1234L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL, 0x20ffffff);

        var msg = new TransactionRecordMsgConverter().fromByteArray(bytes);

        assertEquals((short) 4, msg.getMsgType());
        assertEquals(id, msg.getId());
        assertEquals(1234L, msg.getAmount());
        assertArrayEquals(TestKeyPairs.CONSUME_NODE_A.pubkey(), msg.getConsumeNodePubkey());
        assertArrayEquals(TestKeyPairs.FLOW_NODE_A.pubkey(), msg.getFlowNodePubkey());
        assertArrayEquals(TestKeyPairs.CENTRAL.pubkey(), msg.getCentralPubkey());
    }

    @Test
    void rejectsInvalidMessageLengths() {
        assertThrows(IllegalArgumentException.class, () -> new FlowNodeRegisterMsgConverter().fromByteArray(new byte[122]));
        assertThrows(IllegalArgumentException.class, () -> new CentralPubkeyEmpowerMsgConverter().fromByteArray(new byte[147]));
        assertThrows(IllegalArgumentException.class, () -> new TransactionMountMsgConverter().fromByteArray(new byte[268]));
    }
}
