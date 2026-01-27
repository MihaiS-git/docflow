package com.brutecx.docflow_backend.api.error;


import org.springframework.security.access.AccessDeniedException;

public class LifecycleAccessDeniedException extends AccessDeniedException {

    private final String errorCode;

    public LifecycleAccessDeniedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
