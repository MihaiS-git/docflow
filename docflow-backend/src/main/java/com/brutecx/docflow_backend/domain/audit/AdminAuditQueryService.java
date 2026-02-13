package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int VERIFY_BATCH_SIZE = 1_000;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "timestamp",
            "actorUserId",
            "tenantId",
            "actionType",
            "result"
    );

    private final AdminAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public Page<AdminAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            UUID actorUserId,
            UUID tenantId,
            Instant cursorTimestamp,
            UUID cursorId,
            int page,
            int size,
            String sortField,
            Sort.Direction direction
    ) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        String safeSortField = validateSortField(sortField);

        Sort.Direction safeDirection =
                direction != null ? direction : Sort.Direction.DESC;

        Sort sort = Sort.by(
                new Sort.Order(safeDirection, safeSortField),
                new Sort.Order(safeDirection, "id")
        );

        Pageable pageable = PageRequest.of(
                page,
                safeSize,
                sort
        );

        Specification<AdminAuditEvent> spec = Specification.allOf(
                from != null ? AdminAuditSpecifications.timestampFrom(from) : null,
                to != null ? AdminAuditSpecifications.timestampTo(to) : null,
                correlationId != null && !correlationId.isBlank()
                        ? AdminAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                actorUserId != null
                        ? AdminAuditSpecifications.hasActorUserId(actorUserId)
                        : null,
                tenantId != null
                        ? AdminAuditSpecifications.hasTenantId(tenantId)
                        : null,
                (cursorTimestamp != null && cursorId != null)
                        ? AdminAuditSpecifications.cursorAfter(
                        cursorTimestamp,
                        cursorId,
                        direction == Sort.Direction.ASC)
                        : null
        );

        Page<AdminAuditEvent> resultPage = repository.findAll(spec, pageable);

        recordSensitiveAccess(tenantId);

        return resultPage.map(AdminAuditDTO::from);
    }

    private String validateSortField(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return "timestamp";
        }

        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Unsupported sort field");
        }

        return sortField;
    }

    private void recordSensitiveAccess(UUID requestedTenantId) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        UUID effectiveTenantId =
                requestedTenantId != null
                        ? requestedTenantId
                        : tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "ADMIN_ACTIONS",
                actor.getId().toString(),
                effectiveTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                effectiveTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "ADMIN_ACTIONS",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read admin audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    @Transactional(readOnly = true)
    public void export(
            Instant from,
            Instant to,
            String correlationId,
            UUID actorUserId,
            UUID tenantId,
            java.util.function.Consumer<AdminAuditEvent> consumer
    ) {

        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required for export");
        }

        int exported = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<AdminAuditEvent> spec = Specification.allOf(
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    correlationId != null && !correlationId.isBlank()
                            ? AdminAuditSpecifications.hasCorrelationId(correlationId)
                            : null,
                    actorUserId != null
                            ? AdminAuditSpecifications.hasActorUserId(actorUserId)
                            : null,
                    tenantId != null
                            ? AdminAuditSpecifications.hasTenantId(tenantId)
                            : null,
                    (cursorTimestamp != null && cursorId != null)
                            ? AdminAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorId,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    EXPORT_BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Page<AdminAuditEvent> page = repository.findAll(spec, pageable);

            if (page.isEmpty()) {
                break;
            }

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

    @Transactional(readOnly = true)
    public AdminAuditVerificationResultDTO verify(
            Instant from,
            Instant to,
            UUID tenantId
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
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    tenantId != null
                            ? AdminAuditSpecifications.hasTenantId(tenantId)
                            : null,
                    (cursorTimestamp != null && cursorId != null)
                            ? AdminAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorId,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Page<AdminAuditEvent> page = repository.findAll(spec, pageable);

            if (page.isEmpty()) {
                break;
            }

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

                // Recompute hash using existing EventFingerprint
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

}
