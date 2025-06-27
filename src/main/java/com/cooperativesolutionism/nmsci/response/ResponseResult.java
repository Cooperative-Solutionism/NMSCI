package com.cooperativesolutionism.nmsci.response;

import org.springframework.util.Assert;

public class ResponseResult<T> {

    private int code;
    private String message;
    private T data;

    public ResponseResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(
                ResponseCode.SUCCESS.getCode(),
                ResponseCode.SUCCESS.getMessage(),
                data
        );
    }

    public static <T> ResponseResult<T> failure(ResponseCode responseCode) {
        Assert.isTrue(responseCode.getCode() != ResponseCode.SUCCESS.getCode(), "对于失败响应，响应码不能为SUCCESS");
        return new ResponseResult<>(
                responseCode.getCode(),
                responseCode.getMessage(),
                null
        );
    }

    public static <T> ResponseResult<T> failure(ResponseCode responseCode, T message) {
        Assert.isTrue(responseCode.getCode() != ResponseCode.SUCCESS.getCode(), "对于失败响应，响应码不能为SUCCESS");
        return new ResponseResult<>(
                responseCode.getCode(),
                responseCode.getMessage(),
                message
        );
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
