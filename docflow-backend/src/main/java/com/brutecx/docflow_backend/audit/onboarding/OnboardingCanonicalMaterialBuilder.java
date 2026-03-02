package com.brutecx.docflow_backend.audit.onboarding;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * STRICT GOLD canonical material builder for ONBOARDING stream.
 * RULES:
 *  - Single canonical definition per stream.
 *  - Used by writer + verifier.
 *  - Entity->Input mapping is the single source of truth.
 *  - Explicit immutable field order.
 *  - Versioned.
 *  - Deterministic null handling.
 */
@Component
public final class OnboardingCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<OnboardingCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "ONBOARDING";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;

    public OnboardingCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    public record Input(
            Instant timestamp,
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
            String correlationId,
            String correlationSource,
            String executionContext,
            String ip,
            String userAgent,
            String result,
            OnboardingOutcome outcome,
            String reasonCode,
            String reasonDetail,
            String fingerprint
    ) {
    }

    public Input fromEvent(OnboardingAuditEvent e) {
        Objects.requireNonNull(e, "event must not be null");

        return new Input(
                e.getTimestamp(),
                e.getActorUserId(),
                e.getSubjectId(),
                e.getTenantId(),
                e.getInviteId(),
                e.getCorrelationId(),
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getIp(),
                e.getUserAgent(),
                e.getResult() != null ? e.getResult().name() : null,
                e.getOutcome(),
                e.getReasonCode(),
                e.getReasonDetail(),
                e.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {

        Objects.requireNonNull(in, "canonical input must not be null");
        Objects.requireNonNull(in.outcome(), "outcome must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,
                "timestamp=" + normalizeEpoch(in.timestamp()),
                "actorUserId=" + normalize(in.actorUserId()),
                "subjectId=" + normalize(in.subjectId()),
                "tenantId=" + normalize(in.tenantId()),
                "inviteId=" + normalize(in.inviteId()),
                "correlationId=" + normalize(in.correlationId()),
                "correlationSource=" + normalize(in.correlationSource()),
                "executionContext=" + normalize(in.executionContext()),
                "ip=" + normalize(in.ip()),
                "userAgent=" + normalize(in.userAgent()),
                "result=" + normalize(in.result()),
                "outcome=" + in.outcome().name(),
                "reasonCode=" + normalize(in.reasonCode()),
                "reasonDetail=" + normalize(in.reasonDetail()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String normalize(Object v) {
        return v == null ? NULL_TOKEN : v.toString();
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }

    private String normalizeEpoch(Instant ts) {
        return ts == null ? NULL_TOKEN : String.valueOf(ts.toEpochMilli());
    }
}
