package com.brutecx.docflow_backend.audit.identity;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public final class IdentityProjectionCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<IdentityProjectionCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "IDENTITY_PROJECTION";
    private static final String NULL_TOKEN = "-";

    private final AuditCanonicalVersionProvider versionProvider;

    public IdentityProjectionCanonicalMaterialBuilder(
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
            String subjectId,
            String correlationId,
            String executionContext,
            String correlationSource,
            String result,
            String reasonCode,
            String fingerprint
    ) {
    }

    public Input fromEvent(IdentityProjectionAuditEvent e) {
        Objects.requireNonNull(e, "event must not be null");

        return new Input(
                e.getTimestamp(),
                e.getSubjectId(),
                e.getCorrelationId(),
                e.getExecutionContext() != null ? e.getExecutionContext().name() : null,
                e.getCorrelationSource() != null ? e.getCorrelationSource().name() : null,
                e.getResult() != null ? e.getResult().name() : null,
                e.getReasonCode(),
                e.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {

        Objects.requireNonNull(in, "canonical input must not be null");
        Objects.requireNonNull(in.timestamp(), "timestamp must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,

                "timestamp=" + in.timestamp().toEpochMilli(),
                "subjectId=" + normalize(in.subjectId()),
                "correlationId=" + normalize(in.correlationId()),
                "executionContext=" + normalize(in.executionContext()),
                "correlationSource=" + normalize(in.correlationSource()),
                "result=" + normalize(in.result()),
                "reasonCode=" + normalize(in.reasonCode()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }
}
