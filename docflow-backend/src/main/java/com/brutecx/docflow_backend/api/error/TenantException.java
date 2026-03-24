package com.brutecx.docflow_backend.api.error;

public class TenantException extends ApiException {
    public TenantException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
