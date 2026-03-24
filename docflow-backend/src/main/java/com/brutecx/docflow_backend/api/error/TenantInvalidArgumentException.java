package com.brutecx.docflow_backend.api.error;

public class TenantInvalidArgumentException extends TenantException {

    public TenantInvalidArgumentException(String message) {
        super(ErrorCode.INVALID_ARGUMENT, message);
    }
}