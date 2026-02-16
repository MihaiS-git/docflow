package com.brutecx.docflow_backend.audit.canonical;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docflow.audit.canonical")
public record AuditCanonicalProperties(
        int version
) {
    public AuditCanonicalProperties {
        if (version <= 0) {
            version = 1;
        }
    }
}
