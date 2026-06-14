package com.cooperativesolutionism.nmsci.metadata;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataControllerTest {

    private static final String CONTROLLER_TYPE = "com.cooperativesolutionism.nmsci.controller.MetadataController";
    private static final String MSG_TYPE_DTO = "com.cooperativesolutionism.nmsci.dto.MsgTypeMetadataDTO";
    private static final String CURRENCY_TYPE_DTO = "com.cooperativesolutionism.nmsci.dto.CurrencyTypeMetadataDTO";

    @Test
    void exposesMetadataEndpointContracts() throws Exception {
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Class<?> msgTypeDto = Class.forName(MSG_TYPE_DTO);
        Class<?> currencyTypeDto = Class.forName(CURRENCY_TYPE_DTO);

        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        assertEquals("/metadata", requestMapping.value()[0]);

        Method msgTypesMethod = controllerType.getMethod("getMsgTypes");
        assertEquals(ResponseResult.class, msgTypesMethod.getReturnType());
        assertTrue(msgTypesMethod.getGenericReturnType().getTypeName().contains(msgTypeDto.getName()));
        assertEquals("/message-types", msgTypesMethod.getAnnotation(GetMapping.class).value()[0]);

        Method currencyTypesMethod = controllerType.getMethod("getCurrencyTypes");
        assertEquals(ResponseResult.class, currencyTypesMethod.getReturnType());
        assertTrue(currencyTypesMethod.getGenericReturnType().getTypeName().contains(currencyTypeDto.getName()));
        assertEquals("/currency-types", currencyTypesMethod.getAnnotation(GetMapping.class).value()[0]);
    }

    @Test
    void msgTypesReturnProtocolValuesAndSizeUnits() throws Exception {
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Object controller = controllerType.getConstructor().newInstance();

        ResponseResult<?> result = (ResponseResult<?>) controllerType.getMethod("getMsgTypes").invoke(controller);

        assertEquals(200, result.getCode());
        List<?> data = (List<?>) result.getData();
        assertEquals(MsgTypeEnum.values().length, data.size());

        Object flowNodeRegisterMsg = data.get(0);
        assertEquals("FlowNodeRegisterMsg", value(flowNodeRegisterMsg, "getCode"));
        assertEquals((short) 0x0000, value(flowNodeRegisterMsg, "getValue"));
        assertEquals("0x0000", value(flowNodeRegisterMsg, "getHexValue"));
        assertEquals(123, value(flowNodeRegisterMsg, "getSize"));
        assertEquals("字节", value(flowNodeRegisterMsg, "getSizeUnit"));
        assertEquals("流转节点注册信息", value(flowNodeRegisterMsg, "getName"));
    }

    @Test
    void currencyTypesReturnProtocolValuesAndSmallestUnitDescriptions() throws Exception {
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Object controller = controllerType.getConstructor().newInstance();

        ResponseResult<?> result = (ResponseResult<?>) controllerType.getMethod("getCurrencyTypes").invoke(controller);

        assertEquals(200, result.getCode());
        List<?> data = (List<?>) result.getData();
        assertEquals(CurrencyTypeEnum.values().length, data.size());

        Object gold = data.get(0);
        assertEquals("GOLD", value(gold, "getCode"));
        assertEquals((short) 0, value(gold, "getValue"));
        assertEquals("黄金(微克)", value(gold, "getDescription"));
        assertEquals("微克", value(gold, "getUnit"));
        assertEquals("1 = 1微克黄金", value(gold, "getUnitDescription"));

        Object cny = data.get(1);
        assertEquals("CNY", value(cny, "getCode"));
        assertEquals((short) 1, value(cny, "getValue"));
        assertEquals("人民币(分)", value(cny, "getDescription"));
        assertEquals("分", value(cny, "getUnit"));
        assertEquals("1 = 1分人民币", value(cny, "getUnitDescription"));
    }

    private Object value(Object target, String getter) throws Exception {
        return target.getClass().getMethod(getter).invoke(target);
    }
}
