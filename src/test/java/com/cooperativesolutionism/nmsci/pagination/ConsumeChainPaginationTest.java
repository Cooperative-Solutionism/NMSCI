package com.cooperativesolutionism.nmsci.pagination;

import com.cooperativesolutionism.nmsci.controller.ConsumeChainController;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeChainPaginationTest {

    @Test
    void serviceAndRepositoryQueriesUseSliceAndPageable() throws NoSuchMethodException {
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByMountedTransaction", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByStart", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByStartAndIsLoop", UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByEnd", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByEndAndIsLoop", UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByNode", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByNodeAndIsLoop", UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByRelatedId", UUID.class, UUID.class, UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByPubkey", byte[].class, byte[].class, byte[].class, Boolean.class, Pageable.class);
        assertSliceMethod(
                ConsumeChainQueryService.class,
                "getConsumeChainEdgesById",
                UUID.class, UUID.class, short.class, long.class, long.class, Pageable.class
        );
        assertSliceMethod(
                ConsumeChainQueryService.class,
                "getConsumeChainEdgesByPubkey",
                byte[].class, byte[].class, short.class, long.class, long.class, Pageable.class
        );
        assertSliceMethod(ConsumeChainRepository.class, "findByStart", FlowNodeRegisterMsg.class, Pageable.class);
        assertSliceMethod(ConsumeChainRepository.class, "findByStartAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainRepository.class, "findByEnd", FlowNodeRegisterMsg.class, Pageable.class);
        assertSliceMethod(ConsumeChainRepository.class, "findByEndAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainRepository.class, "findDistinctByNode", FlowNodeRegisterMsg.class, Pageable.class);
        assertSliceMethod(ConsumeChainRepository.class, "findDistinctByNodeAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainRepository.class, "findDistinctByRelatedTransactionMount", TransactionMountMsg.class, Pageable.class);
    }

    @Test
    void controllerBatchQueriesReturnSliceResponse() throws NoSuchMethodException {
        assertSliceResponseControllerMethod(
                "queryConsumeChains",
                String.class, String.class, String.class,
                String.class, String.class, String.class,
                Boolean.class, String.class, int.class, int.class);
        assertSliceResponseControllerMethod(
                "getConsumeChainEdges",
                String.class, String.class, String.class, String.class,
                short.class, long.class, long.class, int.class, int.class
        );
        assertControllerMethodAbsent("getConsumeChainByMountedTransaction", String.class, int.class, int.class);
        assertControllerMethodAbsent("getConsumeChainByRelatedId", String.class, String.class, String.class, Boolean.class, int.class, int.class);
        assertControllerMethodAbsent("getConsumeChainByPubkey", String.class, String.class, String.class, Boolean.class, int.class, int.class);
    }

    @Test
    void edgeNativeQueriesUseStableDistinctOnTieBreaker() throws NoSuchMethodException {
        assertStableEdgeDistinctOnOrder(
                "findConsumeChainEdges",
                UUID.class, UUID.class, short.class, Long.class, Long.class, int.class, long.class
        );
        assertStableEdgeDistinctOnOrder(
                "findConsumeChainEdgesByTarget",
                UUID.class, short.class, Long.class, Long.class, int.class, long.class
        );
    }

    private void assertSliceMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);

        assertEquals(Slice.class, method.getReturnType());
    }

    private void assertSliceResponseControllerMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = ConsumeChainController.class.getMethod(name, parameterTypes);

        assertEquals(ResponseResult.class, method.getReturnType());
        String genericReturnType = method.getGenericReturnType().getTypeName();
        assertTrue(genericReturnType.contains(SliceResponseDTO.class.getName()));
    }

    private void assertControllerMethodAbsent(String name, Class<?>... parameterTypes) {
        assertThrows(NoSuchMethodException.class, () -> ConsumeChainController.class.getMethod(name, parameterTypes));
    }

    private void assertStableEdgeDistinctOnOrder(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Query query = ConsumeChainEdgeRepository.class.getMethod(name, parameterTypes).getAnnotation(Query.class);

        assertTrue(query.value().contains("ORDER BY c.chain, c.related_transaction_mount_timestamp, c.id"));
    }
}
