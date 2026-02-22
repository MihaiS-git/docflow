package com.brutecx.docflow_backend.domain.security;

public class MissingActiveAuditSigningKeyException extends RuntimeException {

    public MissingActiveAuditSigningKeyException() {
        super("No active audit export signing key configured");
    }
}