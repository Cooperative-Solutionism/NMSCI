package com.cooperativesolutionism.nmsci.pagination;

import com.cooperativesolutionism.nmsci.controller.TransactionMountMsgController;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionMountPaginationTest {

    @Test
    void serviceAndRepositoryQueriesUseSliceAndPageable() throws NoSuchMethodException {
        assertSliceMethod(TransactionMountMsgService.class, "getTransactionMountMsgByConsumeNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionMountMsgService.class, "getTransactionMountMsgByFlowNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionMountMsgService.class, "getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey", byte[].class, byte[].class, Pageable.class);
        assertSliceMethod(TransactionMountMsgRepository.class, "findByConsumeNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionMountMsgRepository.class, "findByFlowNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionMountMsgRepository.class, "findByConsumeNodePubkeyAndFlowNodePubkey", byte[].class, byte[].class, Pageable.class);
    }

    @Test
    void controllerBatchQueriesReturnSliceResponse() throws NoSuchMethodException {
        assertSliceResponseControllerMethod(
                "searchTransactionMountMsgs",
                String.class, String.class, String.class, Long.class, Long.class, int.class, int.class);
    }

    private void assertSliceMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);

        assertEquals(Slice.class, method.getReturnType());
    }

    private void assertSliceResponseControllerMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = TransactionMountMsgController.class.getMethod(name, parameterTypes);

        assertEquals(ResponseResult.class, method.getReturnType());
        String genericReturnType = method.getGenericReturnType().getTypeName();
        assertTrue(genericReturnType.contains(SliceResponseDTO.class.getName()));
    }
}
