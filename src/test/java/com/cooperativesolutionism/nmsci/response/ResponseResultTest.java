package com.cooperativesolutionism.nmsci.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseResultTest {

    @Test
    void failureWithDetailUsesDetailAsMessageAndKeepsDataNull() {
        ResponseResult<Void> result = ResponseResult.failure(ResponseCode.BAD_REQUEST, "分页大小必须大于0");

        assertEquals(400, result.getCode());
        assertEquals("分页大小必须大于0", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failureWithBlankDetailFallsBackToResponseCodeMessage() {
        ResponseResult<Void> result = ResponseResult.failure(ResponseCode.NOT_FOUND, " ");

        assertEquals(404, result.getCode());
        assertEquals("Not Found", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void genericFailureKeepsCodeMessageAndNullData() {
        ResponseResult<Object> result = ResponseResult.failure(ResponseCode.CONFLICT);

        assertEquals(409, result.getCode());
        assertEquals("Conflict", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failureRejectsSuccessResponseCode() {
        assertThrows(IllegalArgumentException.class, () -> ResponseResult.failure(ResponseCode.SUCCESS));
        assertThrows(IllegalArgumentException.class, () -> ResponseResult.failure(ResponseCode.SUCCESS, "不能成功"));
    }
}
