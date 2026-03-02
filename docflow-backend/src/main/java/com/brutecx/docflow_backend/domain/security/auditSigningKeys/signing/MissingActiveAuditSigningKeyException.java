package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

public class MissingActiveAuditSigningKeyException extends RuntimeException {

    public MissingActiveAuditSigningKeyException() {
        super("No active audit export signing key configured");
    }
}