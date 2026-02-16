package com.brutecx.docflow_backend.audit.canonical;

import org.springframework.stereotype.Component;

@Component
public class AuditCanonicalVersionProvider {

    private final AuditCanonicalProperties props;

    public AuditCanonicalVersionProvider(AuditCanonicalProperties props) {
        this.props = props;
    }

    public int canonicalVersion() {
        return props.version();
    }
}
