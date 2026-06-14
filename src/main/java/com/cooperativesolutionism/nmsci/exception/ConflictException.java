package com.cooperativesolutionism.nmsci.exception;

/**
 * 资源冲突异常，由 {@link GlobalExceptionHandler} 映射为 HTTP 409。
 * 用于创建时违反唯一性约束的场景（如 id 已存在、公钥已注册/已授权/已冻结、交易已被挂载）。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
