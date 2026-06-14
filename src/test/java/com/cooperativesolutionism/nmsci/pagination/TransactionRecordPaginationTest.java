package com.cooperativesolutionism.nmsci.pagination;

import com.cooperativesolutionism.nmsci.controller.TransactionRecordMsgController;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionRecordPaginationTest {

    @Test
    void serviceAndRepositoryQueriesUseSliceAndPageable() throws NoSuchMethodException {
        assertSliceMethod(TransactionRecordMsgService.class, "getTransactionRecordMsgByConsumeNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionRecordMsgService.class, "getTransactionRecordMsgByFlowNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionRecordMsgService.class, "getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey", byte[].class, byte[].class, Pageable.class);
        assertSliceMethod(TransactionRecordMsgRepository.class, "findByConsumeNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionRecordMsgRepository.class, "findByFlowNodePubkey", byte[].class, Pageable.class);
        assertSliceMethod(TransactionRecordMsgRepository.class, "findByConsumeNodePubkeyAndFlowNodePubkey", byte[].class, byte[].class, Pageable.class);
    }

    @Test
    void controllerBatchQueriesReturnSliceResponse() throws NoSuchMethodException {
        assertSliceResponseControllerMethod(
                "searchTransactionRecordMsgs",
                String.class, String.class, Short.class, Long.class, Long.class, int.class, int.class);
    }

    private void assertSliceMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);

        assertEquals(Slice.class, method.getReturnType());
    }

    private void assertSliceResponseControllerMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = TransactionRecordMsgController.class.getMethod(name, parameterTypes);

        assertEquals(ResponseResult.class, method.getReturnType());
        String genericReturnType = method.getGenericReturnType().getTypeName();
        assertTrue(genericReturnType.contains(SliceResponseDTO.class.getName()));
    }
}
