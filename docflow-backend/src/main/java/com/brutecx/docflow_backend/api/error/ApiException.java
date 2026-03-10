package com.brutecx.docflow_backend.api.error;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return errorCode.httpStatus();
    }
}