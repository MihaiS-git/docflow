package com.brutecx.docflow_backend.audit.unauth;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * STRICT GOLD canonical material builder for UNAUTHENTICATED_ACCESS stream.
 * RULES:
 *  - Single canonical definition per stream.
 *  - Used by writer + verifier.
 *  - Entity->Input mapping is the single source of truth.
 *  - Versioned.
 *  - Deterministic null handling.
 *  - Explicit immutable field ordering.
 */
@Component
public final class UnauthenticatedAccessCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<UnauthenticatedAccessCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "UNAUTHENTICATED_ACCESS";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;

    public UnauthenticatedAccessCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    /**
     * Canonical input aligned 1:1 with persisted UnauthenticatedAccessAuditEvent fields
     * used for integrity verification.
     */
    public record Input(
            Instant timestamp,
            String correlationId,
            String correlationSource,
            String executionContext,
            String result,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String fingerprint
    ) {
    }

    /**
     * Entity → Canonical mapping. Single source of truth.
     */
    public Input fromEvent(UnauthenticatedAccessAuditEvent e) {
        Objects.requireNonNull(e, "event must not be null");

        return new Input(
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getResult() != null ? e.getResult().name() : null,
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {
        Objects.requireNonNull(in, "canonical input must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,
                "timestamp=" + normalizeEpoch(in.timestamp()),
                "correlationId=" + normalize(in.correlationId()),
                "correlationSource=" + normalize(in.correlationSource()),
                "executionContext=" + normalize(in.executionContext()),
                "result=" + normalize(in.result()),
                "httpMethod=" + normalize(in.httpMethod()),
                "path=" + normalize(in.path()),
                "ip=" + normalize(in.ip()),
                "userAgent=" + normalize(in.userAgent()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }

    private String normalizeEpoch(Instant ts) {
        return ts == null ? NULL_TOKEN : String.valueOf(ts.toEpochMilli());
    }
}
