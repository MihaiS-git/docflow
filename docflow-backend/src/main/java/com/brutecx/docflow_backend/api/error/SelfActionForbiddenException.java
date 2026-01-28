package com.brutecx.docflow_backend.api.error;

public class SelfActionForbiddenException extends RuntimeException {
    public SelfActionForbiddenException(String message) {
        super(message);
    }
}
