package com.cooperativesolutionism.nmsci.exception;

import com.cooperativesolutionism.nmsci.response.ResponseCode;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误";
    private static final String VALIDATION_ERROR_MESSAGE = "请求参数非法";
    private static final String CONFLICT_MESSAGE = "数据冲突：违反唯一约束";
    private static final String METHOD_NOT_ALLOWED_MESSAGE = "请求方法不被支持";
    private static final String UNSUPPORTED_MEDIA_TYPE_MESSAGE = "不支持的媒体类型";

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ResponseResult<Void>> handleBadRequest(BadRequestException e) {
        logger.warn("Bad request [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ResponseResult<Void>> handleNotFoundException(NotFoundException e) {
        logger.warn("Not found [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.NOT_FOUND, ResponseCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResponseResult<Void>> handleConflictException(ConflictException e) {
        logger.warn("Conflict [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.CONFLICT, ResponseCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseResult<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        logger.warn("Method not allowed [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.METHOD_NOT_ALLOWED, ResponseCode.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_MESSAGE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ResponseResult<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        logger.warn("Unsupported media type [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ResponseCode.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MEDIA_TYPE_MESSAGE);
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ResponseResult<Void>> handleValidationExceptions(Exception e) {
        logger.warn("Validation failed [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, VALIDATION_ERROR_MESSAGE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseResult<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        logger.warn("Data integrity conflict [{}]: {}", requestContext(), e.getMessage());
        return failure(HttpStatus.CONFLICT, ResponseCode.CONFLICT, CONFLICT_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handleAllExceptions(Exception e) {
        logger.error("An unexpected error occurred [{}]: {}", requestContext(), e.getMessage(), e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    private ResponseEntity<ResponseResult<Void>> failure(HttpStatus status, ResponseCode responseCode, String detail) {
        return ResponseEntity.status(status).body(ResponseResult.failure(responseCode, detail));
    }

    /** 当前请求的「方法 + 路径」，便于将拒绝/错误日志关联到具体端点；非请求上下文返回 "-"。 */
    private static String requestContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            return request.getMethod() + " " + request.getRequestURI();
        }
        return "-";
    }
}
