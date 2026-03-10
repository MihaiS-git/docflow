package com.brutecx.docflow_backend.api.error;

import org.springframework.security.access.AccessDeniedException;

public class LifecycleAccessDeniedException extends AccessDeniedException {

    private final ErrorCode errorCode;

    public LifecycleAccessDeniedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}