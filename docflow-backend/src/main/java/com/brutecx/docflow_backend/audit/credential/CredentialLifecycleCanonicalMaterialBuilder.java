package com.brutecx.docflow_backend.audit.credential;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * STRICT GOLD canonical material builder for CREDENTIAL stream.
 * RULES:
 *  - Single canonical definition per stream.
 *  - Used by writer + verifier.
 *  - Entity->Input mapping is the single source of truth.
 *  - Explicit immutable field order.
 *  - Versioned.
 *  - Deterministic null handling.
 */
@Component
public final class CredentialLifecycleCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<CredentialLifecycleCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "CREDENTIAL";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;

    public CredentialLifecycleCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    /**
     * Canonical input aligned 1:1 with persisted CredentialLifecycleAuditEvent fields used for integrity.
     */
    public record Input(
            Instant timestamp,
            String subjectExternalId,
            String clientId,
            String sessionId,
            String ip,
            CredentialLifecycleEventType eventType,
            String requiredAction,
            String correlationId,
            String correlationSource,
            String executionContext,
            String result,
            String reasonCode,
            String reasonDetail,
            String fingerprint
    ) {
    }

    public Input fromEvent(CredentialLifecycleAuditEvent e) {
        Objects.requireNonNull(e, "event must not be null");

        return new Input(
                e.getTimestamp(),
                e.getSubjectExternalId(),
                e.getClientId(),
                e.getSessionId(),
                e.getIp(),
                e.getEventType(),
                e.getRequiredAction(),
                e.getCorrelationId(),
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getResult() != null ? e.getResult().name() : null,
                e.getReasonCode(),
                e.getReasonDetail(),
                e.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {

        Objects.requireNonNull(in, "canonical input must not be null");
        Objects.requireNonNull(in.eventType(), "eventType must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,

                "timestamp=" + normalizeEpoch(in.timestamp()),
                "subjectExternalId=" + normalize(in.subjectExternalId()),
                "clientId=" + normalize(in.clientId()),
                "sessionId=" + normalize(in.sessionId()),
                "ip=" + normalize(in.ip()),
                "eventType=" + in.eventType().name(),
                "requiredAction=" + normalize(in.requiredAction()),
                "correlationId=" + normalize(in.correlationId()),
                "correlationSource=" + normalize(in.correlationSource()),
                "executionContext=" + normalize(in.executionContext()),
                "result=" + normalize(in.result()),
                "reasonCode=" + normalize(in.reasonCode()),
                "reasonDetail=" + normalize(in.reasonDetail()),
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
