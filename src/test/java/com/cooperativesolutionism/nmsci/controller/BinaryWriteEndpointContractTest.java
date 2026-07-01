package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void binaryWriteEndpointsDeclareCreatedResponseStatus() throws NoSuchMethodException {
        assertCreatedResponseStatus(FlowNodeRegisterMsgController.class, "saveFlowNodeRegisterMsg");
        assertCreatedResponseStatus(CentralPubkeyEmpowerMsgController.class, "saveCentralPubkeyEmpowerMsg");
        assertCreatedResponseStatus(FlowNodeLockedMsgController.class, "saveFlowNodeLockedMsg");
        assertCreatedResponseStatus(CentralPubkeyLockedMsgController.class, "saveCentralPubkeyLockedMsg");
        assertCreatedResponseStatus(TransactionRecordMsgController.class, "saveTransactionRecordMsg");
        assertCreatedResponseStatus(TransactionMountMsgController.class, "saveTransactionMountMsg");
    }

    @Test
    void invalidBinaryBodiesBecomeBadRequestException() {
        FlowNodeRegisterMsgController registerController = new FlowNodeRegisterMsgController(null, new FlowNodeRegisterMsgConverter());
        assertInvalidBody(() -> registerController.saveFlowNodeRegisterMsg(new byte[1]));

        CentralPubkeyEmpowerMsgController empowerController = new CentralPubkeyEmpowerMsgController(null, new CentralPubkeyEmpowerMsgConverter());
        assertInvalidBody(() -> empowerController.saveCentralPubkeyEmpowerMsg(new byte[1]));

        FlowNodeLockedMsgController flowNodeLockedController = new FlowNodeLockedMsgController(null, new FlowNodeLockedMsgConverter());
        assertInvalidBody(() -> flowNodeLockedController.saveFlowNodeLockedMsg(new byte[1]));

        CentralPubkeyLockedMsgController centralLockedController = new CentralPubkeyLockedMsgController(null, new CentralPubkeyLockedMsgConverter());
        assertInvalidBody(() -> centralLockedController.saveCentralPubkeyLockedMsg(new byte[1]));

        TransactionRecordMsgController recordController = new TransactionRecordMsgController(null, new TransactionRecordMsgConverter());
        assertInvalidBody(() -> recordController.saveTransactionRecordMsg(new byte[1]));

        TransactionMountMsgController mountController = new TransactionMountMsgController(null, new TransactionMountMsgConverter());
        assertInvalidBody(() -> mountController.saveTransactionMountMsg(new byte[1]));
    }

    private void assertConsumesOctetStream(Class<?> controllerType, String methodName) throws NoSuchMethodException {
        Method method = controllerType.getMethod(methodName, byte[].class);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);

        assertArrayEquals(new String[]{MediaType.APPLICATION_OCTET_STREAM_VALUE}, postMapping.consumes());
    }

    private void assertCreatedResponseStatus(Class<?> controllerType, String methodName) throws NoSuchMethodException {
        Method method = controllerType.getMethod(methodName, byte[].class);
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        assertNotNull(responseStatus);
        assertEquals(HttpStatus.CREATED, responseStatus.value());
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
