package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import org.springframework.stereotype.Component;

/**
 * Canonical material builder for AUTH stream.
 * Writers + verifiers MUST use the same builder to prevent drift.
 */
@Component
public class AuthenticationCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<AuthenticationCanonicalInput> {

    public static final String STREAM = "AUTH";

    private final CanonicalJsonService canonicalJsonService;
    private final AuditCanonicalVersionProvider versionProvider;

    public AuthenticationCanonicalMaterialBuilder(
            CanonicalJsonService canonicalJsonService,
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.canonicalJsonService = canonicalJsonService;
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    @Override
    public String buildCanonicalMaterial(AuthenticationCanonicalInput in) {
        int cv = versionProvider.canonicalVersion();
        // Versioned + deterministic JSON payload
        return "cv=" + cv + "|" + canonicalJsonService.toCanonicalJson(in);
    }
}
