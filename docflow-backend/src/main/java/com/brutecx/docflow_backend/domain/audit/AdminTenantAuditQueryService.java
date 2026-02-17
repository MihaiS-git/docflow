package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditForensicDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.admin.AdminAuditActionType;
import com.brutecx.docflow_backend.audit.admin.AdminAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
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
import java.util.Objects;
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

    private final AdminAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final AuditChainService auditChainService;

    /* =====================================================
       CURSOR QUERY – timestamp DESC, id DESC
       ===================================================== */

    @Transactional(readOnly = true)
    public Page<AdminAuditDTO> query(
            UUID tenantId,
            Instant from,
            Instant to,
            String correlationId,
            UUID actorUserId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }

        boolean hasCursorTs = cursorTimestamp != null;
        boolean hasCursorId = cursorId != null;
        if (hasCursorTs ^ hasCursorId) {
            throw new IllegalArgumentException("cursorTimestamp and cursorId must be provided together");
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize,
                Sort.by(
                        Sort.Order.desc("timestamp"),
                        Sort.Order.desc("id")
                )
        );

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
                hasCursorTs
                        ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<AdminAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccessTenantScoped(tenantId);

        return page.map(AdminAuditDTO::from);
    }

    /* =====================================================
       FORENSIC EXPORT (ASC, JSONL-friendly)
       - Uses cursor batching internally (timestamp ASC, id ASC)
       - Emits AdminAuditForensicDTO
       ===================================================== */

    @Transactional(readOnly = true)
    public void exportForensic(
            UUID tenantId,
            Instant from,
            Instant to,
            Consumer<AdminAuditForensicDTO> consumer
    ) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required for export");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }
        Objects.requireNonNull(consumer, "consumer is required");

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<AdminAuditEvent> spec = Specification.allOf(
                    AdminAuditSpecifications.hasTenantId(tenantId),
                    (root, query, cb) -> root.get("actionType").in(TENANT_ACTIONS),
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
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
                consumer.accept(AdminAuditForensicDTO.from(event, AdminAuditCanonicalMaterialBuilder.STREAM));
                exported++;

                if (exported >= EXPORT_MAX_ROWS) {
                    recordSensitiveAccessTenantScoped(tenantId);
                    return;
                }

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordSensitiveAccessTenantScoped(tenantId);
    }

    /* =====================================================
       VERIFY – timestamp ASC, id ASC
       - continuity check: prevEventHash must match previous eventHash
       - recompute HMAC using canonical material builder (single source of truth)
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            UUID tenantId,
            Instant from,
            Instant to
    ) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;
        String previousEventHash = null;

        AuditPartition partition = AuditPartition.tenant(AdminAuditCanonicalMaterialBuilder.STREAM, tenantId.toString());

        while (true) {

            Specification<AdminAuditEvent> spec = Specification.allOf(
                    AdminAuditSpecifications.hasTenantId(tenantId),
                    (root, query, cb) -> root.get("actionType").in(TENANT_ACTIONS),
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
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

                if (previousEventHash != null &&
                        !Objects.equals(previousEventHash, normalizeHash(event.getPrevEventHash()))) {

                    recordSensitiveAccessTenantScoped(tenantId);

                    return AuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "Chain continuity mismatch (prevEventHash)"
                    );
                }

                if (event.getChainVersion() > 0 && !"-".equals(event.getEventHash())) {

                    String material = canonicalMaterialBuilder.buildCanonicalMaterial(
                            canonicalMaterialBuilder.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            normalizeHash(event.getPrevEventHash()),
                            material
                    );

                    if (!Objects.equals(expected, event.getEventHash())) {

                        recordSensitiveAccessTenantScoped(tenantId);

                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "Event hash mismatch (recomputed != stored)"
                        );
                    }
                }

                previousEventHash = normalizeHash(event.getEventHash());
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordSensitiveAccessTenantScoped(tenantId);
        return AuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
       SENSITIVE READ AUDIT (tenant-scoped)
       ===================================================== */

    private void recordSensitiveAccessTenantScoped(UUID tenantId) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = auditRequestContextExtractor.fromCurrentRequest();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                AdminAuditCanonicalMaterialBuilder.STREAM,
                "SCOPE_TENANT",
                actor.getId().toString(),
                tenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                AdminAuditCanonicalMaterialBuilder.STREAM,
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read admin audit stream (tenant-scoped tenant actions)",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
