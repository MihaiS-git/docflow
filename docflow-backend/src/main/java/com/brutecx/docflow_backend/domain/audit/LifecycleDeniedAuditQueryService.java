package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEventRepository;
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
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class LifecycleDeniedAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int BATCH_SIZE = 1_000;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of("timestamp");

    private final LifecycleDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    /* =========================
       QUERY
       ========================= */

    @Transactional(readOnly = true)
    public Page<LifecycleDeniedAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Instant cursorTimestamp,
            UUID cursorId,
            int page,
            int size,
            String sortField,
            Sort.Direction direction
    ) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Sort.Direction dir = direction != null ? direction : Sort.Direction.DESC;
        String safeSort = validateSortField(sortField);

        Sort sort = Sort.by(
                new Sort.Order(dir, safeSort),
                new Sort.Order(dir, "id")
        );

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                from != null ? LifecycleDeniedAuditSpecifications.timestampFrom(from) : null,
                to != null ? LifecycleDeniedAuditSpecifications.timestampTo(to) : null,
                correlationId != null && !correlationId.isBlank()
                        ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                subjectId != null && !subjectId.isBlank()
                        ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId)
                        : null,
                (cursorTimestamp != null && cursorId != null)
                        ? LifecycleDeniedAuditSpecifications.cursorAfter(
                        cursorTimestamp,
                        cursorId,
                        dir == Sort.Direction.ASC
                )
                        : null
        );

        Page<LifecycleDeniedAuditEvent> pageResult =
                repository.findAll(spec, pageable);

        recordSensitiveAccess();

        return pageResult.map(LifecycleDeniedAuditDTO::from);
    }

    private static String validateSortField(String sortField) {
        if (sortField == null || sortField.isBlank()) return "timestamp";
        if (!ALLOWED_SORT_FIELDS.contains(sortField))
            throw new IllegalArgumentException("Unsupported sort field");
        return sortField;
    }

    /* =========================
       EXPORT
       ========================= */

    @Transactional(readOnly = true)
    public void export(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Consumer<LifecycleDeniedAuditEvent> consumer
    ) {

        if (from == null || to == null)
            throw new IllegalArgumentException("from and to required");

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorUuid = null;

        while (true) {

            Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                    LifecycleDeniedAuditSpecifications.timestampFrom(from),
                    LifecycleDeniedAuditSpecifications.timestampTo(to),
                    correlationId != null && !correlationId.isBlank()
                            ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId)
                            : null,
                    subjectId != null && !subjectId.isBlank()
                            ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId)
                            : null,
                    (cursorTimestamp != null && cursorUuid != null)
                            ? LifecycleDeniedAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorUuid,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Page<LifecycleDeniedAuditEvent> batch =
                    repository.findAll(spec, pageable);

            if (batch.isEmpty()) break;

            for (LifecycleDeniedAuditEvent e : batch.getContent()) {

                consumer.accept(e);
                exported++;

                if (exported >= EXPORT_MAX_ROWS) {
                    recordSensitiveAccess();
                    return;
                }

                cursorTimestamp = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess();
    }

    /* =========================
       VERIFY (non-chained stream)
       ========================= */

    @Transactional(readOnly = true)
    public long verify(
            Instant from,
            Instant to
    ) {

        if (from == null || to == null)
            throw new IllegalArgumentException("from and to required");

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorUuid = null;

        while (true) {

            Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                    LifecycleDeniedAuditSpecifications.timestampFrom(from),
                    LifecycleDeniedAuditSpecifications.timestampTo(to),
                    (cursorTimestamp != null && cursorUuid != null)
                            ? LifecycleDeniedAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorUuid,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    BATCH_SIZE,
                    Sort.by(
                            Sort.Order.asc("timestamp"),
                            Sort.Order.asc("id")
                    )
            );

            Page<LifecycleDeniedAuditEvent> batch =
                    repository.findAll(spec, pageable);

            if (batch.isEmpty()) break;

            for (LifecycleDeniedAuditEvent e : batch.getContent()) {
                verified++;
                cursorTimestamp = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess();
        return verified;
    }

    /* =========================
       Sensitive read
       ========================= */

    private void recordSensitiveAccess() {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "LIFECYCLE_DENIED",
                actor.getId().toString(),
                rootTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "LIFECYCLE_DENIED",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read lifecycle denied audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
