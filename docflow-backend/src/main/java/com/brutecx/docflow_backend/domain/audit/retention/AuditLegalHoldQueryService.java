package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AuditLegalHoldQueryService {

    private final AuditLegalHoldRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    public AuditLegalHoldQueryService(
            AuditLegalHoldRepository repository,
            ISensitiveAccessAuditService sensitiveAccessAuditService,
            AuditRequestContextExtractor ctxExtractor,
            UserService userService,
            TenantService tenantService
    ) {
        this.repository = repository;
        this.sensitiveAccessAuditService = sensitiveAccessAuditService;
        this.ctxExtractor = ctxExtractor;
        this.userService = userService;
        this.tenantService = tenantService;
    }

    public Page<AuditLegalHold> query(
            String streamName,
            Boolean active,
            UUID eventId,
            String correlationId,
            String caseReferenceId,
            String createdBy,
            Pageable pageable
    ) {
        Specification<AuditLegalHold> spec = Specification.allOf(
                AuditLegalHoldSpecifications.streamNameEqualsIgnoreCase(streamName),
                AuditLegalHoldSpecifications.activeEquals(active),
                AuditLegalHoldSpecifications.eventIdEquals(eventId),
                AuditLegalHoldSpecifications.correlationIdEquals(correlationId),
                AuditLegalHoldSpecifications.caseReferenceIdEquals(caseReferenceId),
                AuditLegalHoldSpecifications.createdByEquals(createdBy)
        );

        Page<AuditLegalHold> page = repository.findAll(spec, pageable);

        // Governance read: legal-hold data is investigation/compliance metadata.
        recordSensitiveAccess("LEGAL_HOLD_LIST");

        return page;
    }

    private void recordSensitiveAccess(String action) {
        try {
            User actor = userService.getRequiredCurrentUser();
            UUID storageTenant = tenantService.getRootTenant().getId();
            AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();

            String fingerprint = EventFingerprint.of(List.of(
                    "SENSITIVE_ACCESS",
                    action,
                    "AUDIT_LEGAL_HOLD",
                    actor.getId().toString(),
                    storageTenant.toString(),
                    ctx.correlationId()
            ));

            sensitiveAccessAuditService.record(
                    actor.getId(),
                    actor.getExternalSubjectId(),
                    storageTenant,
                    SensitiveAccessSubjectType.AUDIT_STREAM,
                    "AUDIT_LEGAL_HOLD",
                    "AUDIT",
                    action,
                    "/api/admin/audit/legal-holds",
                    ctx.correlationId(),
                    ctx.ip(),
                    ctx.userAgent(),
                    action,
                    "Admin legal-hold listing",
                    SensitiveDataClassification.REGULATED,
                    fingerprint
            );
        } catch (Exception ignored) {
            // Do not break business flow; audit service already logs metrics/errors internally.
        }
    }
}