package com.example.demo.common.exception;

public class ApiException extends BusinessException {
    public ApiException(ErrorCode code) {
        super(code, code.getMessage());
    }
}
