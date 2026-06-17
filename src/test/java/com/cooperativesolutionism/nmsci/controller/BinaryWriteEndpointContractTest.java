package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryWriteEndpointContractTest {

    @Test
    void binaryWriteEndpointsDeclareOctetStreamConsumes() throws NoSuchMethodException {
        assertConsumesOctetStream(FlowNodeRegisterMsgController.class, "saveFlowNodeRegisterMsg");
        assertConsumesOctetStream(CentralPubkeyEmpowerMsgController.class, "saveCentralPubkeyEmpowerMsg");
        assertConsumesOctetStream(FlowNodeLockedMsgController.class, "saveFlowNodeLockedMsg");
        assertConsumesOctetStream(CentralPubkeyLockedMsgController.class, "saveCentralPubkeyLockedMsg");
        assertConsumesOctetStream(TransactionRecordMsgController.class, "saveTransactionRecordMsg");
        assertConsumesOctetStream(TransactionMountMsgController.class, "saveTransactionMountMsg");
    }

    @Test
    void invalidBinaryBodiesBecomeBadRequestException() {
        FlowNodeRegisterMsgController registerController = new FlowNodeRegisterMsgController();
        ReflectionTestUtils.setField(registerController, "flowNodeRegisterMsgConverter", new FlowNodeRegisterMsgConverter());
        assertInvalidBody(() -> registerController.saveFlowNodeRegisterMsg(new byte[1]));

        CentralPubkeyEmpowerMsgController empowerController = new CentralPubkeyEmpowerMsgController();
        ReflectionTestUtils.setField(empowerController, "centralPubkeyEmpowerMsgConverter", new CentralPubkeyEmpowerMsgConverter());
        assertInvalidBody(() -> empowerController.saveCentralPubkeyEmpowerMsg(new byte[1]));

        FlowNodeLockedMsgController flowNodeLockedController = new FlowNodeLockedMsgController();
        ReflectionTestUtils.setField(flowNodeLockedController, "flowNodeLockedMsgConverter", new FlowNodeLockedMsgConverter());
        assertInvalidBody(() -> flowNodeLockedController.saveFlowNodeLockedMsg(new byte[1]));

        CentralPubkeyLockedMsgController centralLockedController = new CentralPubkeyLockedMsgController();
        ReflectionTestUtils.setField(centralLockedController, "centralPubkeyLockedMsgConverter", new CentralPubkeyLockedMsgConverter());
        assertInvalidBody(() -> centralLockedController.saveCentralPubkeyLockedMsg(new byte[1]));

        TransactionRecordMsgController recordController = new TransactionRecordMsgController();
        ReflectionTestUtils.setField(recordController, "transactionRecordMsgConverter", new TransactionRecordMsgConverter());
        assertInvalidBody(() -> recordController.saveTransactionRecordMsg(new byte[1]));

        TransactionMountMsgController mountController = new TransactionMountMsgController();
        ReflectionTestUtils.setField(mountController, "transactionMountMsgConverter", new TransactionMountMsgConverter());
        assertInvalidBody(() -> mountController.saveTransactionMountMsg(new byte[1]));
    }

    private void assertConsumesOctetStream(Class<?> controllerType, String methodName) throws NoSuchMethodException {
        Method method = controllerType.getMethod(methodName, byte[].class);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);

        assertArrayEquals(new String[]{MediaType.APPLICATION_OCTET_STREAM_VALUE}, postMapping.consumes());
    }

    private void assertInvalidBody(ThrowingCall call) {
        BadRequestException exception = assertThrows(BadRequestException.class, call::run);

        assertTrue(exception.getMessage().startsWith("Invalid byte array size, expected "));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
