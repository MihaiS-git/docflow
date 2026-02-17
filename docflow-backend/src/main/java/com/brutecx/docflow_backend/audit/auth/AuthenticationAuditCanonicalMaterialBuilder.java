package com.brutecx.docflow_backend.audit.auth;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * STRICT GOLD canonical material builder for AUTH stream.
 * RULES:
 *  - Single canonical definition per stream.
 *  - Used by writer + verifier.
 *  - Entity->Input mapping is the single source of truth.
 *  - Explicit immutable field order.
 *  - Versioned.
 *  - Deterministic null handling.
 */
@Component
public final class AuthenticationAuditCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<AuthenticationAuditCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "AUTH";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;

    public AuthenticationAuditCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    /**
     * Canonical input aligned 1:1 with persisted AuthenticationEvent fields used for integrity.
     * Any structural change requires canonical version bump.
     */
    public record Input(
            Instant timestamp,
            AuthenticationEventSource source,
            String username,
            String subjectId,
            AuthenticationResult result,
            String idp,
            String ip,
            String userAgent,
            String correlationId,
            String correlationSource,
            String executionContext,
            String auditResult,
            String fingerprint
    ) {
    }

    /**
     * Entity → Canonical mapping. Single source of truth.
     */
    public Input fromEvent(AuthenticationEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        return new Input(
                event.getTimestamp(),
                event.getSource(),
                event.getUsername(),
                event.getSubjectId(),
                event.getResult(),
                event.getIdp(),
                event.getIp(),
                event.getUserAgent(),
                event.getCorrelationId(),
                event.getCorrelationSource() != null ? event.getCorrelationSource().name() : null,
                event.getExecutionContext() != null ? event.getExecutionContext().name() : null,
                event.getAuditResult() != null ? event.getAuditResult().name() : null,
                event.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {

        Objects.requireNonNull(in, "canonical input must not be null");
        Objects.requireNonNull(in.timestamp(), "timestamp must not be null");
        Objects.requireNonNull(in.result(), "result must not be null");
        Objects.requireNonNull(in.source(), "source must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,

                "timestamp=" + in.timestamp().toEpochMilli(),
                "source=" + in.source().name(),
                "username=" + normalize(in.username()),
                "subjectId=" + normalize(in.subjectId()),
                "result=" + in.result().name(),
                "idp=" + normalize(in.idp()),
                "ip=" + normalize(in.ip()),
                "userAgent=" + normalize(in.userAgent()),
                "correlationId=" + normalize(in.correlationId()),
                "correlationSource=" + normalize(in.correlationSource()),
                "executionContext=" + normalize(in.executionContext()),
                "auditResult=" + normalize(in.auditResult()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private static String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }
}
