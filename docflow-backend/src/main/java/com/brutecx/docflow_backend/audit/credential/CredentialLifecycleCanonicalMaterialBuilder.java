package com.brutecx.docflow_backend.audit.credential;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import org.springframework.stereotype.Component;

@Component
public class CredentialLifecycleCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<CredentialLifecycleCanonicalInput> {

    public static final String STREAM = "CREDENTIAL";

    private final CanonicalJsonService canonicalJsonService;
    private final AuditCanonicalVersionProvider versionProvider;

    public CredentialLifecycleCanonicalMaterialBuilder(
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
    public String buildCanonicalMaterial(CredentialLifecycleCanonicalInput input) {
        int cv = versionProvider.canonicalVersion();

        return "cv=" + cv + "|" +
                canonicalJsonService.toCanonicalJson(input);
    }
}
