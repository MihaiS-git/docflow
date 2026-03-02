package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.LegalHoldAuditMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class AuditLegalHoldService {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final AuditLegalHoldRepository repository;
    private final IAdminAuditEventService adminAuditService;

    public AuditLegalHoldService(
            AuditLegalHoldRepository repository,
            IAdminAuditEventService adminAuditService
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.adminAuditService = Objects.requireNonNull(adminAuditService);
    }

    @Transactional
    public AuditLegalHold createHold(
            String streamName,
            UUID eventId,
            String correlationId,
            String caseReferenceId,
            String reason,
            UUID tenantId,
            UUID targetUserId
    ) {
        String normalizedStream = normalize(streamName);

        AuditLegalHold hold = new AuditLegalHold(
                normalizedStream,
                eventId,
                correlationId,
                caseReferenceId,
                reason,
                targetUserId != null ? targetUserId.toString() : "SYSTEM"
        );

        AuditLegalHold saved = repository.save(hold);

        LegalHoldAuditMetadata metadata =
                new LegalHoldAuditMetadata(
                        normalizedStream,
                        eventId != null ? eventId.toString() : null,
                        correlationId,
                        caseReferenceId,
                        reason
                );

        adminAuditService.record(
                AdminAuditActionType.LEGAL_HOLD_CREATE,
                tenantId,
                "AUDIT_LEGAL_HOLD",
                targetUserId,
                metadata
        );

        log.info("security_event",
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", "legal_hold_create"),
                kv("event.outcome", "success"),
                kv("correlation.id", correlationId),
                kv("audit.stream", normalizedStream),
                kv("audit.hold_id", saved.getId()),
                kv("audit.case_reference", caseReferenceId),
                kv("tenant.id", tenantId),
                kv("target.user_id", targetUserId)
        );

        return saved;
    }

    @Transactional
    public void deactivate(
            UUID holdId,
            UUID tenantId,
            UUID targetUserId
    ) {
        AuditLegalHold hold = repository.findById(holdId).orElse(null);

        if (hold == null) {
            log.warn("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "legal_hold_deactivate"),
                    kv("event.outcome", "failure"),
                    kv("error.code", "LEGAL_HOLD_NOT_FOUND"),
                    kv("error.reason", "hold_not_found"),
                    kv("audit.hold_id", holdId),
                    kv("tenant.id", tenantId),
                    kv("target.user_id", targetUserId)
            );
            throw new IllegalArgumentException("Legal hold not found: " + holdId);
        }

        if (!hold.isActive()) {
            log.info("security_event",
                    kv("schema_version", "docflow_siem_v1"),
                    kv("event.category", "audit"),
                    kv("event.action", "legal_hold_deactivate"),
                    kv("event.outcome", "noop"),
                    kv("correlation.id", hold.getCorrelationId()),
                    kv("audit.hold_id", holdId),
                    kv("audit.case_reference", hold.getCaseReferenceId()),
                    kv("tenant.id", tenantId),
                    kv("target.user_id", targetUserId)
            );
            return;
        }

        hold.deactivate();

        LegalHoldAuditMetadata metadata =
                new LegalHoldAuditMetadata(
                        hold.getStreamName(),
                        hold.getEventId() != null ? hold.getEventId().toString() : null,
                        hold.getCorrelationId(),
                        hold.getCaseReferenceId(),
                        "DEACTIVATED"
                );

        adminAuditService.record(
                AdminAuditActionType.LEGAL_HOLD_DEACTIVATE,
                tenantId,
                "AUDIT_LEGAL_HOLD",
                targetUserId,
                metadata
        );

        log.info("security_event",
                kv("schema_version", "docflow_siem_v1"),
                kv("event.category", "audit"),
                kv("event.action", "legal_hold_deactivate"),
                kv("event.outcome", "success"),
                kv("correlation.id", hold.getCorrelationId()),
                kv("audit.hold_id", holdId),
                kv("audit.case_reference", hold.getCaseReferenceId()),
                kv("tenant.id", tenantId),
                kv("target.user_id", targetUserId)
        );
    }

    private String normalize(String stream) {
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("streamName must not be blank");
        }
        return stream.trim().toUpperCase(Locale.ROOT);
    }
}