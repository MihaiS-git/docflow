package com.brutecx.docflow_backend.audit.sensitive;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * STRICT GOLD canonical material builder for SENSITIVE_ACCESS stream.
 * Used by writer + verifier.
 * Entity->Input mapping is the single source of truth.
 * Versioned.
 * Deterministic.
 * Explicit immutable field ordering.
 */
@Component
public final class SensitiveAccessCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<SensitiveAccessCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "SENSITIVE_ACCESS";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;

    public SensitiveAccessCanonicalMaterialBuilder(
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
            String actorExternalSubjectId,
            UUID tenantId,
            SensitiveAccessSubjectType subjectType,
            String subjectId,
            String resource,
            String action,
            String resourcePath,
            String correlationId,
            String correlationSource,
            String executionContext,
            String result,
            String ip,
            String userAgent,
            String reasonCode,
            String reasonDetail,
            SensitiveDataClassification dataClassification,
            String fingerprint
    ) {
    }

    public Input fromEvent(SensitiveAccessAuditEvent e) {
        Objects.requireNonNull(e, "event must not be null");

        return new Input(
                e.getTimestamp(),
                e.getActorUserId(),
                e.getActorExternalSubjectId(),
                e.getTenantId(),
                e.getSubjectType(),
                e.getSubjectId(),
                e.getResource(),
                e.getAction(),
                e.getResourcePath(),
                e.getCorrelationId(),
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getResult() != null ? e.getResult().name() : null,
                e.getIp(),
                e.getUserAgent(),
                e.getReasonCode(),
                e.getReasonDetail(),
                e.getDataClassification(),
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
                "actorUserId=" + normalize(in.actorUserId()),
                "actorExternalSubjectId=" + normalize(in.actorExternalSubjectId()),
                "tenantId=" + normalize(in.tenantId()),
                "subjectType=" + normalizeEnum(in.subjectType()),
                "subjectId=" + normalize(in.subjectId()),
                "resource=" + normalize(in.resource()),
                "action=" + normalize(in.action()),
                "resourcePath=" + normalize(in.resourcePath()),
                "correlationId=" + normalize(in.correlationId()),
                "correlationSource=" + normalize(in.correlationSource()),
                "executionContext=" + normalize(in.executionContext()),
                "result=" + normalize(in.result()),
                "ip=" + normalize(in.ip()),
                "userAgent=" + normalize(in.userAgent()),
                "reasonCode=" + normalize(in.reasonCode()),
                "reasonDetail=" + normalize(in.reasonDetail()),
                "dataClassification=" + normalizeEnum(in.dataClassification()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }

    private String normalize(Object v) {
        return v == null ? NULL_TOKEN : v.toString();
    }

    private String normalizeEnum(Enum<?> e) {
        return e == null ? NULL_TOKEN : e.name();
    }

    private String normalizeEpoch(Instant ts) {
        return ts == null ? NULL_TOKEN : String.valueOf(ts.toEpochMilli());
    }
}
