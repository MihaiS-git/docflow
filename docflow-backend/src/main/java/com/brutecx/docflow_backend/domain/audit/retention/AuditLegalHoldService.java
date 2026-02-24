package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.IAdminAuditEventService;
import com.brutecx.docflow_backend.audit.admin.LegalHoldAuditMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

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

        log.info(
                "audit_legal_hold created stream={} caseRef={} tenantId={}",
                normalizedStream,
                caseReferenceId,
                tenantId
        );

        return saved;
    }

    @Transactional
    public void deactivate(
            UUID holdId,
            UUID tenantId,
            UUID targetUserId
    ) {

        AuditLegalHold hold = repository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Legal hold not found: " + holdId));

        if (!hold.isActive()) return;

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

        log.info(
                "audit_legal_hold deactivated holdId={} caseRef={}",
                holdId,
                hold.getCaseReferenceId()
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLegalHold> findActiveByStream(String streamName) {
        return repository.findByStreamNameAndActiveIsTrue(normalize(streamName));
    }

    private String normalize(String stream) {
        if (stream == null || stream.isBlank()) {
            throw new IllegalArgumentException("streamName must not be blank");
        }
        return stream.trim().toUpperCase(Locale.ROOT);
    }
}