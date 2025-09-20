package com.cooperativesolutionism.nmsci.exception;

import com.cooperativesolutionism.nmsci.response.ResponseCode;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseResult<String> handleAllExceptions(Exception e) {
        logger.error("An unexpected error occurred: {}", e.getMessage(), e);
        return ResponseResult.failure(ResponseCode.INTERNAL_SERVER_ERROR, e.getMessage());
    }
}
