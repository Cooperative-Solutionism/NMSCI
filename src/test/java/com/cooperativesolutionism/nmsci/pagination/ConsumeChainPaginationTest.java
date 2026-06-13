package com.cooperativesolutionism.nmsci.pagination;

import com.cooperativesolutionism.nmsci.controller.ConsumeChainController;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeChainPaginationTest {

    @Test
    void serviceAndRepositoryQueriesUseSliceAndPageable() throws NoSuchMethodException {
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByMountedTransaction", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByStart", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByStartAndIsLoop", UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByEnd", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByEndAndIsLoop", UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByNode", UUID.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByNodeAndIsLoop", UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByRelatedId", UUID.class, UUID.class, UUID.class, Boolean.class, Pageable.class);
        assertSliceMethod(ConsumeChainService.class, "getConsumeChainByPubkey", byte[].class, byte[].class, byte[].class, Boolean.class, Pageable.class);
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
        assertSliceResponseControllerMethod("getConsumeChainByMountedTransaction", String.class, int.class, int.class);
        assertSliceResponseControllerMethod("getConsumeChainByRelatedId", String.class, String.class, String.class, Boolean.class, int.class, int.class);
        assertSliceResponseControllerMethod("getConsumeChainByPubkey", String.class, String.class, String.class, Boolean.class, int.class, int.class);
        assertControllerMethodAbsent("getConsumeChainByStart", String.class, Boolean.class, int.class, int.class);
        assertControllerMethodAbsent("getConsumeChainByEnd", String.class, Boolean.class, int.class, int.class);
        assertControllerMethodAbsent("getConsumeChainByNode", String.class, Boolean.class, int.class, int.class);
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
}
