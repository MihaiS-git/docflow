package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AdminTenantAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int VERIFY_BATCH_SIZE = 1_000;

    private static final EnumSet<AdminAuditActionType> TENANT_ACTIONS =
            EnumSet.of(
                    AdminAuditActionType.TENANT_CREATED,
                    AdminAuditActionType.TENANT_UPDATED,
                    AdminAuditActionType.TENANT_SUSPENDED,
                    AdminAuditActionType.TENANT_MUTATION_DENIED
            );

    private final AdminAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;

    /* =========================
       QUERY (Cursor + Offset)
       ========================= */

    @Transactional(readOnly = true)
    public Page<AdminAuditEvent> query(
            UUID tenantId,
            Instant from,
            Instant to,
            String correlationId,
            UUID actorUserId,
            Instant cursorTimestamp,
            UUID cursorId,
            int page,
            int size,
            Sort.Direction direction
    ) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Sort.Direction safeDirection = direction != null ? direction : Sort.Direction.DESC;

        Specification<AdminAuditEvent> spec = Specification.allOf(
                AdminAuditSpecifications.hasTenantId(tenantId),
                (root, query, cb) -> root.get("actionType").in(TENANT_ACTIONS),
                from != null ? AdminAuditSpecifications.timestampFrom(from) : null,
                to != null ? AdminAuditSpecifications.timestampTo(to) : null,
                correlationId != null && !correlationId.isBlank()
                        ? AdminAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                actorUserId != null
                        ? AdminAuditSpecifications.hasActorUserId(actorUserId)
                        : null,
                (cursorTimestamp != null && cursorId != null)
                        ? AdminAuditSpecifications.cursorAfter(
                        cursorTimestamp,
                        cursorId,
                        safeDirection == Sort.Direction.ASC
                )
                        : null
        );

        Pageable pageable = PageRequest.of(
                page,
                safeSize,
                Sort.by(
                        new Sort.Order(safeDirection, "timestamp"),
                        new Sort.Order(safeDirection, "id")
                )
        );

        Page<AdminAuditEvent> result = repository.findAll(spec, pageable);

        recordSensitiveAccess(tenantId);

        return result;
    }

    /* =========================
       EXPORT
       ========================= */

    @Transactional(readOnly = true)
    public void export(
            UUID tenantId,
            Instant from,
            Instant to,
            Consumer<AdminAuditEvent> consumer
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required for export");
        }

        int exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<AdminAuditEvent> spec = Specification.allOf(
                    AdminAuditSpecifications.hasTenantId(tenantId),
                    (root, query, cb) -> root.get("actionType").in(TENANT_ACTIONS),
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    (cursorTimestamp != null && cursorId != null)
                            ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    EXPORT_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<AdminAuditEvent> page = repository.findAll(spec, pageable);

            if (page.isEmpty()) break;

            for (AdminAuditEvent event : page.getContent()) {

                consumer.accept(event);
                exported++;

                if (exported >= EXPORT_MAX_ROWS) {
                    recordSensitiveAccess(tenantId);
                    return;
                }

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordSensitiveAccess(tenantId);
    }

    /* =========================
       VERIFY
       ========================= */

    @Transactional(readOnly = true)
    public AdminAuditVerificationResultDTO verify(
            UUID tenantId,
            Instant from,
            Instant to
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required for verification");
        }

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;
        String previousHash = null;

        while (true) {

            Specification<AdminAuditEvent> spec = Specification.allOf(
                    AdminAuditSpecifications.hasTenantId(tenantId),
                    (root, query, cb) -> root.get("actionType").in(TENANT_ACTIONS),
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    (cursorTimestamp != null && cursorId != null)
                            ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<AdminAuditEvent> page = repository.findAll(spec, pageable);

            if (page.isEmpty()) break;

            for (AdminAuditEvent event : page.getContent()) {

                if (previousHash != null &&
                        !event.getPrevEventHash().equals(previousHash)) {

                    recordSensitiveAccess(tenantId);
                    return AdminAuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "Chain continuity mismatch"
                    );
                }

                String recomputed = EventFingerprint.of(List.of(
                        event.getActorUserId().toString(),
                        event.getTenantId().toString(),
                        event.getActionType().name(),
                        event.getResult().name(),
                        event.getCorrelationId(),
                        event.getEventFingerprint()
                ));

                if (!event.getEventHash().equals(recomputed)) {

                    recordSensitiveAccess(tenantId);
                    return AdminAuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "Event hash mismatch"
                    );
                }

                previousHash = event.getEventHash();
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordSensitiveAccess(tenantId);
        return AdminAuditVerificationResultDTO.success(verified);
    }

    /* =========================
       Sensitive Read Logging
       ========================= */

    private void recordSensitiveAccess(UUID tenantId) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = auditRequestContextExtractor.fromCurrentRequest();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "ADMIN_TENANT_ACTIONS",
                actor.getId().toString(),
                tenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "ADMIN_TENANT_ACTIONS",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read tenant admin audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
