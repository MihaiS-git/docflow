package com.brutecx.docflow_backend.api.error;

public class TenantValidationException extends TenantException {

    public TenantValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}