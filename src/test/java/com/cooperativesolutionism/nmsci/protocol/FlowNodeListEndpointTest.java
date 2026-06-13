package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.service.impl.FlowNodeRegisterMsgServiceImpl;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowNodeListEndpointTest {

    private static final String DTO_TYPE = "com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO";
    private static final String CONTROLLER_TYPE = "com.cooperativesolutionism.nmsci.controller.FlowNodeController";

    @Test
    void exposesFlowNodeListEndpointContract() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);

        Method controllerMethod = controllerType.getMethod(
                "listFlowNodes",
                Boolean.class,
                Boolean.class,
                Boolean.class,
                int.class,
                int.class
        );
        assertEquals(ResponseResult.class, controllerMethod.getReturnType());
        String controllerReturnType = controllerMethod.getGenericReturnType().getTypeName();
        assertTrue(controllerReturnType.contains("com.cooperativesolutionism.nmsci.dto.SliceResponseDTO"));
        assertTrue(controllerReturnType.contains(dtoType.getName()));

        Method serviceMethod = FlowNodeRegisterMsgService.class.getMethod(
                "listFlowNodes",
                Boolean.class,
                Boolean.class,
                Boolean.class,
                Pageable.class
        );
        assertEquals(Slice.class, serviceMethod.getReturnType());
        assertTrue(serviceMethod.getGenericReturnType().getTypeName().contains(dtoType.getName()));

        Method repositoryMethod = FlowNodeRegisterMsgRepository.class.getMethod(
                "findFlowNodeList",
                Boolean.class,
                Boolean.class,
                byte[].class,
                Pageable.class
        );
        assertEquals(Slice.class, repositoryMethod.getReturnType());
        assertTrue(repositoryMethod.getGenericReturnType().getTypeName().contains(dtoType.getName()));
    }

    @Test
    void controllerPassesFiltersAndPageableToService() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Object dto = dtoType
                .getConstructor(UUID.class, byte[].class, boolean.class, boolean.class, boolean.class, boolean.class)
                .newInstance(UUID.randomUUID(), TestKeyPairs.FLOW_NODE_A.pubkey(), true, true, false, true);
        Slice<?> slice = new SliceImpl<>(List.of(dto));
        AtomicReference<Boolean> registeredFilter = new AtomicReference<>();
        AtomicReference<Boolean> authorizedFilter = new AtomicReference<>();
        AtomicReference<Boolean> lockedFilter = new AtomicReference<>();
        AtomicReference<Pageable> pageable = new AtomicReference<>();
        FlowNodeRegisterMsgService service = serviceProxy(
                slice,
                registeredFilter,
                authorizedFilter,
                lockedFilter,
                pageable
        );
        Class<?> controllerType = Class.forName(CONTROLLER_TYPE);
        Object controller = controllerType.getConstructor().newInstance();
        ReflectionTestUtils.setField(controller, "flowNodeRegisterMsgService", service);

        ResponseResult<?> result = (ResponseResult<?>) controllerType
                .getMethod("listFlowNodes", Boolean.class, Boolean.class, Boolean.class, int.class, int.class)
                .invoke(controller, true, true, false, 1, 25);

        assertEquals(200, result.getCode());
        assertEquals(true, registeredFilter.get());
        assertEquals(true, authorizedFilter.get());
        assertEquals(false, lockedFilter.get());
        assertEquals(1, pageable.get().getPageNumber());
        assertEquals(25, pageable.get().getPageSize());
        Object response = result.getData();
        Object content = response.getClass().getMethod("getContent").invoke(response);
        assertSame(dto, ((List<?>) content).get(0));
    }

    @Test
    void serviceReadsCurrentCentralPubkeyAndDelegatesToRepository() {
        byte[] currentCentralPubkey = TestKeyPairs.CENTRAL.pubkey();
        Pageable pageable = Pageable.ofSize(50);
        Slice<?> slice = new SliceImpl<>(List.of());
        AtomicReference<Boolean> authorizedFilter = new AtomicReference<>();
        AtomicReference<Boolean> lockedFilter = new AtomicReference<>();
        AtomicReference<byte[]> queriedCurrentCentralPubkey = new AtomicReference<>();
        AtomicReference<Pageable> queriedPageable = new AtomicReference<>();
        FlowNodeRegisterMsgRepository repository = repositoryProxy(
                slice,
                authorizedFilter,
                lockedFilter,
                queriedCurrentCentralPubkey,
                queriedPageable
        );
        CentralPubkeyValidator centralPubkeyValidator = mock(CentralPubkeyValidator.class);
        when(centralPubkeyValidator.currentCentralPubkey()).thenReturn(currentCentralPubkey);
        FlowNodeRegisterMsgServiceImpl service = new FlowNodeRegisterMsgServiceImpl();
        ReflectionTestUtils.setField(service, "flowNodeRegisterMsgRepository", repository);
        ReflectionTestUtils.setField(service, "centralPubkeyValidator", centralPubkeyValidator);

        Slice<?> result = service.listFlowNodes(true, true, false, pageable);

        assertSame(slice, result);
        assertEquals(true, authorizedFilter.get());
        assertEquals(false, lockedFilter.get());
        assertArrayEquals(currentCentralPubkey, queriedCurrentCentralPubkey.get());
        assertSame(pageable, queriedPageable.get());
    }

    @Test
    void serviceReturnsEmptySliceWhenRegisteredFilterIsFalse() {
        Pageable pageable = Pageable.ofSize(50);
        FlowNodeRegisterMsgRepository repository = repositoryProxy(
                new SliceImpl<>(List.of("unexpected")),
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>()
        );
        FlowNodeRegisterMsgServiceImpl service = new FlowNodeRegisterMsgServiceImpl();
        ReflectionTestUtils.setField(service, "flowNodeRegisterMsgRepository", repository);

        Slice<?> result = service.listFlowNodes(false, null, null, pageable);

        assertTrue(result.isEmpty());
        assertSame(pageable, result.getPageable());
    }

    private FlowNodeRegisterMsgService serviceProxy(
            Slice<?> slice,
            AtomicReference<Boolean> registeredFilter,
            AtomicReference<Boolean> authorizedFilter,
            AtomicReference<Boolean> lockedFilter,
            AtomicReference<Pageable> pageable
    ) {
        return (FlowNodeRegisterMsgService) Proxy.newProxyInstance(
                FlowNodeRegisterMsgService.class.getClassLoader(),
                new Class<?>[]{FlowNodeRegisterMsgService.class},
                (proxy, method, args) -> {
                    if ("listFlowNodes".equals(method.getName())) {
                        registeredFilter.set((Boolean) args[0]);
                        authorizedFilter.set((Boolean) args[1]);
                        lockedFilter.set((Boolean) args[2]);
                        pageable.set((Pageable) args[3]);
                        return slice;
                    }
                    return objectMethod(proxy, method, args, "FlowNodeRegisterMsgService");
                }
        );
    }

    private FlowNodeRegisterMsgRepository repositoryProxy(
            Slice<?> slice,
            AtomicReference<Boolean> authorizedFilter,
            AtomicReference<Boolean> lockedFilter,
            AtomicReference<byte[]> queriedCurrentCentralPubkey,
            AtomicReference<Pageable> pageable
    ) {
        return (FlowNodeRegisterMsgRepository) Proxy.newProxyInstance(
                FlowNodeRegisterMsgRepository.class.getClassLoader(),
                new Class<?>[]{FlowNodeRegisterMsgRepository.class},
                (proxy, method, args) -> {
                    if ("findFlowNodeList".equals(method.getName())) {
                        authorizedFilter.set((Boolean) args[0]);
                        lockedFilter.set((Boolean) args[1]);
                        queriedCurrentCentralPubkey.set((byte[]) args[2]);
                        pageable.set((Pageable) args[3]);
                        return slice;
                    }
                    return objectMethod(proxy, method, args, "FlowNodeRegisterMsgRepository");
                }
        );
    }

    private Object objectMethod(Object proxy, Method method, Object[] args, String name) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> name;
            default -> throw new UnsupportedOperationException(method.toGenericString());
        };
    }
}
