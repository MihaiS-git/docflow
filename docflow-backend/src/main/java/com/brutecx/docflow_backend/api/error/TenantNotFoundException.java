package com.brutecx.docflow_backend.api.error;

public class TenantNotFoundException extends TenantException {

    public TenantNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}