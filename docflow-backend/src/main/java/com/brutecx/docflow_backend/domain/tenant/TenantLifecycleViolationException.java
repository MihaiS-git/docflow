package com.brutecx.docflow_backend.domain.tenant;

public class TenantLifecycleViolationException extends RuntimeException {
    public TenantLifecycleViolationException(String message) {
        super(message);
    }
}
