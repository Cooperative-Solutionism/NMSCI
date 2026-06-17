package com.cooperativesolutionism.nmsci.exception;

/**
 * 错误请求异常，由 {@link GlobalExceptionHandler} 映射为 HTTP 400。
 * 用于请求参数缺失或非法等客户端错误场景。
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
