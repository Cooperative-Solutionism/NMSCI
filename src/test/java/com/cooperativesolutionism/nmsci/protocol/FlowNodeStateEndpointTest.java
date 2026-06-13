package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowNodeStateEndpointTest {

    private static final String DTO_TYPE = "com.cooperativesolutionism.nmsci.dto.FlowNodeStateResponseDTO";
    private static final String OVERVIEW_TYPE = "com.cooperativesolutionism.nmsci.protocol.FlowNodeStateOverview";
    private static final String CONTROLLER_TYPE = "com.cooperativesolutionism.nmsci.controller.FlowNodeController";

    @Test
    void exposesFlowNodeStateEndpointContract() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Class<?> overviewType = Class.forName(OVERVIEW_TYPE);
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);

        Method controllerMethod = controllerType.getMethod("getFlowNodeState", String.class);
        assertEquals(ResponseResult.class, controllerMethod.getReturnType());
        assertTrue(controllerMethod.getGenericReturnType().getTypeName().contains(dtoType.getName()));

        Method serviceMethod = FlowNodeRegisterMsgService.class.getMethod("getFlowNodeState", byte[].class);
        assertEquals(dtoType, serviceMethod.getReturnType());

        Method repositoryMethod = FlowNodeRegisterMsgRepository.class.getMethod(
                "findFlowNodeStateOverview",
                byte[].class,
                byte[].class
        );
        assertEquals(overviewType, repositoryMethod.getReturnType());
    }

    @Test
    void serviceReadsCurrentCentralPubkeyAndMapsOverview() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Class<?> overviewType = Class.forName(OVERVIEW_TYPE);
        byte[] flowNodePubkey = TestKeyPairs.FLOW_NODE_A.pubkey();
        byte[] currentCentralPubkey = TestKeyPairs.CENTRAL.pubkey();
        Object overview = flowNodeStateOverview(overviewType, true, true, false, true);
        AtomicReference<byte[]> queriedFlowNodePubkey = new AtomicReference<>();
        AtomicReference<byte[]> queriedCurrentCentralPubkey = new AtomicReference<>();

        FlowNodeRegisterMsgRepository repository = repositoryProxy(
                overview,
                queriedFlowNodePubkey,
                queriedCurrentCentralPubkey
        );
        CentralPubkeyValidator centralPubkeyValidator = mock(CentralPubkeyValidator.class);
        when(centralPubkeyValidator.currentCentralPubkey()).thenReturn(currentCentralPubkey);

        FlowNodeRegisterMsgService service = new FlowNodeRegisterMsgService();
        ReflectionTestUtils.setField(service, "flowNodeRegisterMsgRepository", repository);
        ReflectionTestUtils.setField(service, "centralPubkeyValidator", centralPubkeyValidator);

        Object response = FlowNodeRegisterMsgService.class
                .getMethod("getFlowNodeState", byte[].class)
                .invoke(service, flowNodePubkey);

        assertEquals(dtoType, response.getClass());
        assertArrayEquals(flowNodePubkey, queriedFlowNodePubkey.get());
        assertArrayEquals(currentCentralPubkey, queriedCurrentCentralPubkey.get());
        assertFlowNodeStateResponse(response, true, true, false, true);
    }

    @Test
    void controllerParsesHexParameterAndWrapsServiceResponse() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Object dto = dtoType.getConstructor().newInstance();
        setBoolean(dto, "setRegistered", true);
        setBoolean(dto, "setAuthorized", true);
        setBoolean(dto, "setLocked", false);
        setBoolean(dto, "setCurrentCentralPubkeyAuthorized", true);

        AtomicReference<byte[]> serviceFlowNodePubkey = new AtomicReference<>();
        FlowNodeRegisterMsgService service = serviceProxy(dto, serviceFlowNodePubkey);
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Object controller = controllerType.getConstructor().newInstance();
        ReflectionTestUtils.setField(controller, "flowNodeRegisterMsgService", service);

        ResponseResult<?> result = (ResponseResult<?>) controllerType
                .getMethod("getFlowNodeState", String.class)
                .invoke(controller, ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()));

        assertEquals(200, result.getCode());
        assertArrayEquals(TestKeyPairs.FLOW_NODE_A.pubkey(), serviceFlowNodePubkey.get());
        assertSame(dto, result.getData());
    }

    @Test
    void dtoMapsOverviewBooleans() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Class<?> overviewType = Class.forName(OVERVIEW_TYPE);
        Object overview = flowNodeStateOverview(overviewType, false, true, true, false);

        Object dto = dtoType.getMethod("from", overviewType).invoke(null, overview);

        assertFlowNodeStateResponse(dto, false, true, true, false);
    }

    private Object flowNodeStateOverview(
            Class<?> overviewType,
            boolean registered,
            boolean authorized,
            boolean locked,
            boolean currentCentralPubkeyAuthorized
    ) {
        return Proxy.newProxyInstance(
                overviewType.getClassLoader(),
                new Class<?>[]{overviewType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRegistered" -> registered;
                    case "getAuthorized" -> authorized;
                    case "getLocked" -> locked;
                    case "getCurrentCentralPubkeyAuthorized" -> currentCentralPubkeyAuthorized;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "FlowNodeStateOverview";
                    default -> throw new UnsupportedOperationException(method.toGenericString());
                }
        );
    }

    private FlowNodeRegisterMsgRepository repositoryProxy(
            Object overview,
            AtomicReference<byte[]> queriedFlowNodePubkey,
            AtomicReference<byte[]> queriedCurrentCentralPubkey
    ) {
        return (FlowNodeRegisterMsgRepository) Proxy.newProxyInstance(
                FlowNodeRegisterMsgRepository.class.getClassLoader(),
                new Class<?>[]{FlowNodeRegisterMsgRepository.class},
                (proxy, method, args) -> {
                    if ("findFlowNodeStateOverview".equals(method.getName())) {
                        queriedFlowNodePubkey.set((byte[]) args[0]);
                        queriedCurrentCentralPubkey.set((byte[]) args[1]);
                        return overview;
                    }
                    return objectMethod(proxy, method, args, "FlowNodeRegisterMsgRepository");
                }
        );
    }

    private FlowNodeRegisterMsgService serviceProxy(Object dto, AtomicReference<byte[]> serviceFlowNodePubkey) {
        FlowNodeRegisterMsgService service = mock(FlowNodeRegisterMsgService.class);
        when(service.getFlowNodeState(any(byte[].class))).thenAnswer(invocation -> {
            serviceFlowNodePubkey.set(invocation.getArgument(0));
            return dto;
        });
        return service;
    }

    private Object objectMethod(Object proxy, Method method, Object[] args, String name) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> name;
            default -> throw new UnsupportedOperationException(method.toGenericString());
        };
    }

    private void assertFlowNodeStateResponse(
            Object response,
            boolean registered,
            boolean authorized,
            boolean locked,
            boolean currentCentralPubkeyAuthorized
    ) throws Exception {
        assertEquals(registered, response.getClass().getMethod("getRegistered").invoke(response));
        assertEquals(authorized, response.getClass().getMethod("getAuthorized").invoke(response));
        assertEquals(locked, response.getClass().getMethod("getLocked").invoke(response));
        assertEquals(
                currentCentralPubkeyAuthorized,
                response.getClass().getMethod("getCurrentCentralPubkeyAuthorized").invoke(response)
        );
    }

    private void setBoolean(Object target, String methodName, boolean value) throws Exception {
        target.getClass().getMethod(methodName, boolean.class).invoke(target, value);
    }
}
