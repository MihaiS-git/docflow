package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.canonical.AuditCanonicalVersionProvider;
import com.brutecx.docflow_backend.audit.canonical.CanonicalJsonService;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Canonical material builder for the ADMIN_ACTIONS audit stream.
 * This MUST be used by both:
 * - writer (AdminAuditEventServiceImpl)
 * - verifier (AdminAuditQueryService)
 * <p>
 * Field order is explicit and stable.
 */
@Component
public class AdminAuditCanonicalMaterialBuilder implements AuditCanonicalMaterialBuilder<AdminAuditCanonicalMaterialBuilder.Input> {

    public static final String STREAM = "ADMIN_ACTIONS";

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

    public record Input(
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

    public Input fromEvent(AdminAuditEvent event) {
        return new Input(
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
        int cv = versionProvider.canonicalVersion();

        // NOTE: explicit field order; do not reorder without bumping canonical version.
        return String.join("|",
                "cv=" + cv,
                "actorUserId=" + in.actorUserId(),
                "subjectId=" + in.subjectId(),
                "tenantId=" + in.tenantId(),
                "actionType=" + in.actionType().name(),
                "result=" + in.result().name(),
                "correlationId=" + in.correlationId(),
                "targetUserId=" + (in.targetUserId() != null ? in.targetUserId() : "-"),
                "metadata=" + (in.metadata() != null ? canonicalJsonService.toCanonicalJson(in.metadata()) : "null"),
                "fingerprint=" + in.fingerprint()
        );
    }
}
