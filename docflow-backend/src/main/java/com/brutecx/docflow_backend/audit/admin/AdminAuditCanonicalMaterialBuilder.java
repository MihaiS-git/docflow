package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * STRICT GOLD canonical material builder for ADMIN_ACTIONS stream.
 * RULES:
 *  - Single canonical definition per stream.
 *  - Used by writer + verifier.
 *  - Entity->Input mapping is the single source of truth.
 *  - Explicit immutable field order.
 *  - Versioned.
 *  - Deterministic null handling.
 *  - Metadata serialized deterministically.
 */
@Component
public final class AdminAuditCanonicalMaterialBuilder
        implements AuditCanonicalMaterialBuilder<AdminAuditCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "ADMIN_ACTIONS";
    private static final String NULL_TOKEN = "-";

    private final CanonicalJsonService canonicalJsonService;
    private final AuditCanonicalVersionProvider versionProvider;

    public AdminAuditCanonicalMaterialBuilder(
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

    /**
     * Canonical input aligned 1:1 with persisted AdminAuditEvent fields used for integrity.
     * Any structural change requires canonical version bump.
     */
    public record Input(
            Instant timestamp,
            UUID actorUserId,
            String subjectId,
            UUID tenantId,
            AdminAuditActionType actionType,
            AuditResult result,
            String correlationId,
            UUID targetUserId,
            AdminAuditMetadata metadata,
            String fingerprint
    ) {
    }

    /**
     * Entity → Canonical mapping. Single source of truth.
     */
    public Input fromEvent(AdminAuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        return new Input(
                event.getTimestamp(),
                event.getActorUserId(),
                event.getSubjectId(),
                event.getTenantId(),
                event.getActionType(),
                event.getResult(),
                event.getCorrelationId(),
                event.getTargetUserId(),
                event.getMetadata(),
                event.getEventFingerprint()
        );
    }

    @Override
    public String buildCanonicalMaterial(Input in) {
        Objects.requireNonNull(in, "canonical input must not be null");
        Objects.requireNonNull(in.timestamp(), "timestamp must not be null");
        Objects.requireNonNull(in.actionType(), "actionType must not be null");
        Objects.requireNonNull(in.result(), "result must not be null");

        int cv = versionProvider.canonicalVersion();

        return String.join("|",
                "cv=" + cv,
                "stream=" + STREAM,

                "timestamp=" + in.timestamp().toEpochMilli(),

                "actorUserId=" + normalize(in.actorUserId()),
                "subjectId=" + normalize(in.subjectId()),
                "tenantId=" + normalize(in.tenantId()),
                "actionType=" + in.actionType().name(),
                "result=" + in.result().name(),
                "correlationId=" + normalize(in.correlationId()),
                "targetUserId=" + normalize(in.targetUserId()),
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

    private String normalizeMetadata(AdminAuditMetadata metadata) {
        if (metadata == null) {
            return NULL_TOKEN;
        }
        return canonicalJsonService.toCanonicalJson(metadata);
    }
}
