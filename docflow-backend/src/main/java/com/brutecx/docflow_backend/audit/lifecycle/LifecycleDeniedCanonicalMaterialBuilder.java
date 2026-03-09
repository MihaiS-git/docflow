package com.brutecx.docflow_backend.audit.lifecycle;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * STRICT GOLD canonical material builder for LIFECYCLE_DENIED stream.
 * Used by writer + verifier.
 * Versioned.
 * Deterministic.
 * Explicit immutable field order.
 */
@Component
public final class LifecycleDeniedCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<LifecycleDeniedCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "LIFECYCLE_DENIED";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;
    private final ObjectMapper objectMapper;

    public LifecycleDeniedCanonicalMaterialBuilder(
            AuditCanonicalVersionProvider versionProvider,
            ObjectMapper objectMapper
    ) {
        this.versionProvider = versionProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    public record Input(
            Instant timestamp,
            String correlationId,
            String correlationSource,
            String executionContext,
            String result,
            String subjectId,
            String reasonCode,
            String httpMethod,
            String path,
            String ip,
            String userAgent,
            String fingerprint,
            LifecycleAuditMetadata metadata
    ) {
    }

    public Input fromEvent(LifecycleDeniedAuditEvent e) {
        Objects.requireNonNull(e, "event must not be null");

        return new Input(
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getResult() != null ? e.getResult().name() : null,
                e.getSubjectId(),
                e.getReasonCode(),
                e.getHttpMethod(),
                e.getPath(),
                e.getIp(),
                e.getUserAgent(),
                e.getEventFingerprint(),
                e.getMetadata()
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

                "subjectId=" + normalize(in.subjectId()),
                "reasonCode=" + normalize(in.reasonCode()),
                "httpMethod=" + normalize(in.httpMethod()),
                "path=" + normalize(in.path()),
                "ip=" + normalize(in.ip()),
                "userAgent=" + normalize(in.userAgent()),

                "metadata=" + normalizeMetadata(in.metadata()),

                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }

    private String normalizeEpoch(Instant ts) {
        return ts == null ? NULL_TOKEN : String.valueOf(ts.toEpochMilli());
    }

    private String normalizeMetadata(LifecycleAuditMetadata metadata) {
        if (metadata == null) {
            return NULL_TOKEN;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to canonicalize lifecycle metadata", e);
        }
    }
}