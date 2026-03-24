package com.brutecx.docflow_backend.api.error;

public class TenantAlreadyExistsException extends TenantException {

    public TenantAlreadyExistsException(String message) {
        super(ErrorCode.TENANT_ALREADY_EXISTS, message);
    }
}