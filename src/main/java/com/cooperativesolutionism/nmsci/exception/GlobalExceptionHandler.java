package com.cooperativesolutionism.nmsci.exception;

import com.cooperativesolutionism.nmsci.response.ResponseCode;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误";
    private static final String CONFLICT_MESSAGE = "数据冲突：违反唯一约束";

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseResult<String>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("Bad request: {}", e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ResponseResult<String>> handleValidationExceptions(Exception e) {
        logger.warn("Validation failed: {}", e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseResult<String>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        logger.warn("Data integrity conflict: {}", e.getMessage());
        return failure(HttpStatus.CONFLICT, ResponseCode.CONFLICT, CONFLICT_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<String>> handleAllExceptions(Exception e) {
        logger.error("An unexpected error occurred: {}", e.getMessage(), e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    private ResponseEntity<ResponseResult<String>> failure(HttpStatus status, ResponseCode responseCode, String detail) {
        return ResponseEntity.status(status).body(ResponseResult.failure(responseCode, detail));
    }
}
