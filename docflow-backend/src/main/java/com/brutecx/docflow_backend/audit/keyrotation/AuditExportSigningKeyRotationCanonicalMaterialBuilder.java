package com.brutecx.docflow_backend.audit.keyrotation;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public final class AuditExportSigningKeyRotationCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<AuditExportSigningKeyRotationCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "AUDIT_EXPORT_SIGNING_KEY_ROTATION";
    private static final String NULL_TOKEN = "-";

    private final CanonicalJsonService canonicalJsonService;
    private final AuditCanonicalVersionProvider versionProvider;

    public AuditExportSigningKeyRotationCanonicalMaterialBuilder(
            CanonicalJsonService canonicalJsonService,
            AuditCanonicalVersionProvider versionProvider
    ) {
        this.canonicalJsonService = canonicalJsonService;
        this.versionProvider = versionProvider;
    }

    @Override
    public String stream() {
        return STREAM;
    }

    public record Input(
            Instant timestamp,
            String correlationId,
            AuditExportSigningKeyRotationMetadata metadata,
            String fingerprint
    ) {}

    public Input fromEvent(AuditExportSigningKeyRotationEvent e) {
        Objects.requireNonNull(e, "event must not be null");
        return new Input(
                e.getTimestamp(),
                e.getCorrelationId(),
                e.getMetadata(),
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
                "correlationId=" + normalize(in.correlationId()),
                "metadata=" + normalizeMetadata(in.metadata()),
                "fingerprint=" + normalize(in.fingerprint())
        );
    }

    private String normalize(Object v) {
        return (v == null) ? NULL_TOKEN : v.toString();
    }

    private String normalize(String v) {
        return (v == null || v.isBlank()) ? NULL_TOKEN : v.trim();
    }

    private String normalizeMetadata(AuditExportSigningKeyRotationMetadata metadata) {
        if (metadata == null) return NULL_TOKEN;
        return canonicalJsonService.toCanonicalJson(metadata);
    }
}