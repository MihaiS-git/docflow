package com.brutecx.docflow_backend.audit.unauth;

import org.springframework.stereotype.Component;

@Component
public class UnauthenticatedAccessCanonicalMaterialBuilder {

    public static final String STREAM = "UNAUTHENTICATED_ACCESS";

    public String buildCanonicalMaterial(UnauthenticatedAccessCanonicalInput in) {
        return String.join("|",
                STREAM,
                String.valueOf(in.timestamp()),                 // must be present at write-time
                nullSafe(in.correlationId()),
                in.correlationSource().name(),
                in.executionContext().name(),
                in.result().name(),
                nullSafe(in.httpMethod()),
                nullSafe(in.path()),
                nullSafe(in.ip()),
                nullSafe(in.userAgent()),
                nullSafe(in.eventFingerprint())
        );
    }

    private static String nullSafe(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
