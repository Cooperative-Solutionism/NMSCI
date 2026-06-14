package com.cooperativesolutionism.nmsci.exception;

/**
 * 资源不存在异常，由 {@link GlobalExceptionHandler} 映射为 HTTP 404。
 * 用于按 id/高度/哈希/公钥等查询但目标资源不存在的场景。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
