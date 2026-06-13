package com.cooperativesolutionism.nmsci.system;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemParamsControllerTest {

    private static final String CONTROLLER_TYPE = "com.cooperativesolutionism.nmsci.controller.SystemController";
    private static final String DTO_TYPE = "com.cooperativesolutionism.nmsci.dto.SystemParamsDTO";

    @Test
    void exposesSystemParamsEndpointContract() throws Exception {
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Class<?> dtoType = Class.forName(DTO_TYPE);

        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        assertEquals("/system", requestMapping.value()[0]);

        Method method = controllerType.getMethod("getParams");
        assertEquals(ResponseResult.class, method.getReturnType());
        assertTrue(method.getGenericReturnType().getTypeName().contains(dtoType.getName()));
        assertEquals("/params", method.getAnnotation(GetMapping.class).value()[0]);
    }

    @Test
    void returnsCurrentConfigAndLatestBlockParams() throws Exception {
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Object controller = controllerType.getConstructor().newInstance();
        NmsciProperties properties = properties();
        byte[] latestBlockHash = bytes(0x22, 32);
        BlockInfo latestBlock = new BlockInfo();
        latestBlock.setId(latestBlockHash);
        latestBlock.setHeight(42L);
        BlockChainService blockChainService = mock(BlockChainService.class);
        when(blockChainService.getLastBlock()).thenReturn(latestBlock);
        ReflectionTestUtils.setField(controller, "nmsciProperties", properties);
        ReflectionTestUtils.setField(controller, "blockChainService", blockChainService);

        ResponseResult<?> result = (ResponseResult<?>) controllerType.getMethod("getParams").invoke(controller);

        assertEquals(200, result.getCode());
        Object data = result.getData();
        assertEquals(7, value(data, "getBlockVersion"));
        assertEquals(ByteArrayUtil.bytesToHex(TestKeyPairs.CENTRAL.pubkey()), value(data, "getCentralPubkey"));
        assertEquals(0x1d0e4916, value(data, "getRegisterDifficultyTargetNbits"));
        assertEquals("0x1d0e4916", value(data, "getRegisterDifficultyTargetNbitsHex"));
        assertEquals(0x20ffffff, value(data, "getTransactionDifficultyTargetNbits"));
        assertEquals("0x20ffffff", value(data, "getTransactionDifficultyTargetNbitsHex"));
        assertEquals(properties.getSourceCodeZipHash(), value(data, "getSourceCodeZipHash"));
        assertEquals(42L, value(data, "getLatestBlockHeight"));
        assertEquals(ByteArrayUtil.bytesToHex(latestBlockHash), value(data, "getLatestBlockHash"));
    }

    @Test
    void returnsNullLatestBlockFieldsWhenNoBlockExists() throws Exception {
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Object controller = controllerType.getConstructor().newInstance();
        BlockChainService blockChainService = mock(BlockChainService.class);
        when(blockChainService.getLastBlock()).thenReturn(null);
        ReflectionTestUtils.setField(controller, "nmsciProperties", properties());
        ReflectionTestUtils.setField(controller, "blockChainService", blockChainService);

        ResponseResult<?> result = (ResponseResult<?>) controllerType.getMethod("getParams").invoke(controller);

        Object data = result.getData();
        assertNull(value(data, "getLatestBlockHeight"));
        assertNull(value(data, "getLatestBlockHash"));
    }

    private NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        properties.setBlockVersion(7);
        properties.setRegisterDifficultyTargetNbits(0x1d0e4916);
        properties.setTransactionDifficultyTargetNbits(0x20ffffff);
        properties.setSourceCodeZipHash("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");
        return properties;
    }

    private byte[] bytes(int value, int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private Object value(Object target, String getter) throws Exception {
        return target.getClass().getMethod(getter).invoke(target);
    }
}
