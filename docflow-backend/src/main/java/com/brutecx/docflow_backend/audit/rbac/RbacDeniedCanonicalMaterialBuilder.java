package com.brutecx.docflow_backend.audit.rbac;

import org.springframework.stereotype.Component;

@Component
public class RbacDeniedCanonicalMaterialBuilder {

    public static final String STREAM = "RBAC_DENIED";

    public String stream() {
        return STREAM;
    }

    /**
     * Canonical material MUST be identical between writer + verifier.
     * Keep it simple, explicit, and stable.
     */
    public String buildCanonicalMaterial(RbacDeniedCanonicalInput in) {
        return String.join("|",
                STREAM,
                n(in.subjectId()),
                n(in.httpMethod()),
                n(in.path()),
                n(in.ip()),
                n(in.userAgent()),
                n(in.correlationId()),
                n(in.eventFingerprint()),
                String.valueOf(in.timestamp() != null ? in.timestamp().toEpochMilli() : 0L)
        );
    }

    private static String n(String v) {
        return (v == null || v.isBlank()) ? "-" : v.trim();
    }
}
