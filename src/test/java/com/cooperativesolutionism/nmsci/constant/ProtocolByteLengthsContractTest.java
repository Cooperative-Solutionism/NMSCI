package com.cooperativesolutionism.nmsci.constant;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.controller.CentralPubkeyEmpowerMsgController;
import com.cooperativesolutionism.nmsci.controller.CentralPubkeyLockedMsgController;
import com.cooperativesolutionism.nmsci.controller.FlowNodeLockedMsgController;
import com.cooperativesolutionism.nmsci.controller.FlowNodeRegisterMsgController;
import com.cooperativesolutionism.nmsci.controller.TransactionMountMsgController;
import com.cooperativesolutionism.nmsci.controller.TransactionRecordMsgController;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.converter.MessageConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolByteLengthsContractTest {

    @Test
    void messageTypeSizesUseProtocolByteLengths() {
        assertSize(MsgTypeEnum.FlowNodeRegisterMsg,
                ProtocolByteLengths.FLOW_NODE_REGISTER_STORED_BYTES,
                ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES);
        assertSize(MsgTypeEnum.CentralPubkeyEmpowerMsg,
                ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_STORED_BYTES,
                ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES);
        assertSize(MsgTypeEnum.CentralPubkeyLockedMsg,
                ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_STORED_BYTES,
                ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES);
        assertSize(MsgTypeEnum.FlowNodeLockedMsg,
                ProtocolByteLengths.FLOW_NODE_LOCKED_STORED_BYTES,
                ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES);
        assertSize(MsgTypeEnum.TransactionRecordMsg,
                ProtocolByteLengths.TRANSACTION_RECORD_STORED_BYTES,
                ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES);
        assertSize(MsgTypeEnum.TransactionMountMsg,
                ProtocolByteLengths.TRANSACTION_MOUNT_STORED_BYTES,
                ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES);
    }

    @Test
    void converterExpectedSizesMatchMessageTypeInboundSizes() {
        for (MessageConverter<?> converter : List.<MessageConverter<?>>of(
                new FlowNodeRegisterMsgConverter(),
                new CentralPubkeyEmpowerMsgConverter(),
                new CentralPubkeyLockedMsgConverter(),
                new FlowNodeLockedMsgConverter(),
                new TransactionRecordMsgConverter(),
                new TransactionMountMsgConverter()
        )) {
            assertEquals(converter.msgType().getInboundSize(), converter.expectedSize(), converter.msgType().name());
        }
    }

    @Test
    void writeControllerByteArraySizeAnnotationsMatchInboundSizes() throws Exception {
        assertByteArraySize(FlowNodeRegisterMsgController.class, "saveFlowNodeRegisterMsg",
                ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES);
        assertByteArraySize(CentralPubkeyEmpowerMsgController.class, "saveCentralPubkeyEmpowerMsg",
                ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES);
        assertByteArraySize(CentralPubkeyLockedMsgController.class, "saveCentralPubkeyLockedMsg",
                ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES);
        assertByteArraySize(FlowNodeLockedMsgController.class, "saveFlowNodeLockedMsg",
                ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES);
        assertByteArraySize(TransactionRecordMsgController.class, "saveTransactionRecordMsg",
                ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES);
        assertByteArraySize(TransactionMountMsgController.class, "saveTransactionMountMsg",
                ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES);
    }

    private static void assertSize(MsgTypeEnum msgType, int storedBytes, int inboundBytes) {
        assertEquals(storedBytes, msgType.getSize(), msgType.name() + " stored bytes");
        assertEquals(inboundBytes, msgType.getInboundSize(), msgType.name() + " inbound bytes");
    }

    private static void assertByteArraySize(Class<?> controllerClass, String methodName, int expectedValue)
            throws Exception {
        Method method = controllerClass.getMethod(methodName, byte[].class);
        ByteArraySize byteArraySize = method.getParameters()[0].getAnnotation(ByteArraySize.class);

        assertEquals(expectedValue, byteArraySize.value(), controllerClass.getSimpleName() + "." + methodName);
    }
}
