package com.brutecx.docflow_backend.audit.sensitive;

import org.springframework.stereotype.Component;

@Component
public class SensitiveAccessCanonicalMaterialBuilder {

    public static final String STREAM = "SENSITIVE_ACCESS";

    public String stream() {
        return STREAM;
    }

    public String buildCanonicalMaterial(SensitiveAccessCanonicalInput in) {
        return String.join("|",
                STREAM,
                n(in.reasonCode()),
                n(in.subjectType() != null ? in.subjectType().name() : null),
                n(in.subjectId()),
                n(in.resource()),
                n(in.action()),
                n(in.resourcePath()),
                n(in.dataClassification() != null ? in.dataClassification().name() : null),
                n(in.actorUserId() != null ? in.actorUserId().toString() : null),
                n(in.actorExternalSubjectId()),
                n(in.tenantId() != null ? in.tenantId().toString() : null),
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
