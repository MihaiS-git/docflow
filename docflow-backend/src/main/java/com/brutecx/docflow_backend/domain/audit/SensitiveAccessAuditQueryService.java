package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.sensitive.*;
import com.brutecx.docflow_backend.audit.tamper.AuditChainHasher;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final String STREAM = "SENSITIVE_ACCESS";

    private final SensitiveAccessAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    @Value("${docflow.audit.chain.secret:}")
    private String chainSecret;

    @Transactional(readOnly = true)
    public Page<SensitiveAccessAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size,
            Sort.Direction direction
    ) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction dir = direction != null ? direction : Sort.Direction.DESC;

        // Deterministic ordering: timestamp + id
        Sort sort = Sort.by(
                new Sort.Order(dir, "timestamp"),
                new Sort.Order(dir, "id")
        );

        Pageable pageable = PageRequest.of(0, safeSize, sort);

        Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                from != null ? SensitiveAccessAuditSpecifications.timestampFrom(from) : null,
                to != null ? SensitiveAccessAuditSpecifications.timestampTo(to) : null,
                (correlationId != null && !correlationId.isBlank())
                        ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                (subjectId != null && !subjectId.isBlank())
                        ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId)
                        : null,
                (tenantId != null)
                        ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId)
                        : null,
                (actorUserId != null)
                        ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId)
                        : null,
                (cursorTimestamp != null && cursorId != null)
                        ? SensitiveAccessAuditSpecifications.cursor(
                        cursorTimestamp,
                        cursorId,
                        dir == Sort.Direction.ASC
                                ? SensitiveAccessAuditSpecifications.SortDirection.ASC
                                : SensitiveAccessAuditSpecifications.SortDirection.DESC
                )
                        : null
        );

        Page<SensitiveAccessAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccessRead();

        return page.map(SensitiveAccessAuditDTO::from);
    }

    @Transactional(readOnly = true)
    public void export(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId,
            Consumer<SensitiveAccessAuditEvent> consumer
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to required");
        }

        long exported = 0;
        Instant cursorTs = null;
        UUID cursorUuid = null;

        try {
            while (true) {
                Pageable pageable = PageRequest.of(
                        0,
                        EXPORT_BATCH_SIZE,
                        Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                );

                Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                        SensitiveAccessAuditSpecifications.timestampFrom(from),
                        SensitiveAccessAuditSpecifications.timestampTo(to),
                        (correlationId != null && !correlationId.isBlank())
                                ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId)
                                : null,
                        (subjectId != null && !subjectId.isBlank())
                                ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId)
                                : null,
                        (tenantId != null)
                                ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId)
                                : null,
                        (actorUserId != null)
                                ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId)
                                : null,
                        (cursorTs != null && cursorUuid != null)
                                ? SensitiveAccessAuditSpecifications.cursor(
                                cursorTs,
                                cursorUuid,
                                SensitiveAccessAuditSpecifications.SortDirection.ASC
                        )
                                : null
                );

                Page<SensitiveAccessAuditEvent> batch = repository.findAll(spec, pageable);
                if (batch.isEmpty()) {
                    return;
                }

                for (SensitiveAccessAuditEvent e : batch.getContent()) {
                    consumer.accept(e);
                    exported++;

                    if (exported >= EXPORT_MAX_ROWS) {
                        return;
                    }

                    cursorTs = e.getTimestamp();
                    cursorUuid = e.getId();
                }
            }
        } finally {
            // meta-audit: always record read for export, even if capped/errored
            recordSensitiveAccessRead();
        }
    }

    @Transactional(readOnly = true)
    public SensitiveAccessAuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to required");
        }

        if (chainSecret == null || chainSecret.isBlank()) {
            recordSensitiveAccessRead();
            return SensitiveAccessAuditVerificationResultDTO.failure(
                    0,
                    null,
                    null,
                    "Audit chain secret missing (docflow.audit.chain.secret)"
            );
        }

        long verified = 0;
        Instant cursorTs = null;
        UUID cursorUuid = null;

        // per-tenant chain partition (matches write path)
        Map<UUID, String> lastHashByTenant = new HashMap<>();

        try {
            while (true) {
                Pageable pageable = PageRequest.of(
                        0,
                        VERIFY_BATCH_SIZE,
                        Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                );

                Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                        SensitiveAccessAuditSpecifications.timestampFrom(from),
                        SensitiveAccessAuditSpecifications.timestampTo(to),
                        (cursorTs != null && cursorUuid != null)
                                ? SensitiveAccessAuditSpecifications.cursor(
                                cursorTs,
                                cursorUuid,
                                SensitiveAccessAuditSpecifications.SortDirection.ASC
                        )
                                : null
                );

                Page<SensitiveAccessAuditEvent> batch = repository.findAll(spec, pageable);
                if (batch.isEmpty()) {
                    return SensitiveAccessAuditVerificationResultDTO.success(verified);
                }

                for (SensitiveAccessAuditEvent e : batch.getContent()) {
                    UUID tenantId = e.getTenantId();

                    String previousHash = lastHashByTenant.get(tenantId);
                    if (previousHash != null && !Objects.equals(e.getPrevEventHash(), previousHash)) {
                        return SensitiveAccessAuditVerificationResultDTO.failure(
                                verified,
                                e.getId(),
                                tenantId,
                                "Chain continuity mismatch: expectedPrev=" + previousHash +
                                        " actualPrev=" + e.getPrevEventHash()
                        );
                    }

                    // EXACT canonical write-path material (SensitiveAccessAuditServiceImpl)
                    String eventMaterial = String.join("|",
                            STREAM,
                            String.valueOf(e.getActorUserId()),
                            String.valueOf(e.getActorExternalSubjectId()),
                            tenantId.toString(),
                            String.valueOf(e.getSubjectType()),
                            e.getSubjectId(),
                            e.getResource(),
                            e.getAction(),
                            e.getResourcePath(),
                            e.getCorrelationId(),
                            e.getReasonCode(),
                            e.getDataClassification() != null ? e.getDataClassification().name() : "UNSPECIFIED",
                            e.getEventFingerprint()
                    );

                    // AuditChainService canonical material: v1|STREAM|tenantId|prevHash|eventMaterial
                    String chainMaterial =
                            "v1|" + STREAM + "|" +
                                    tenantId + "|" +
                                    e.getPrevEventHash() + "|" +
                                    eventMaterial;

                    String recomputed = AuditChainHasher.hmacSha256Hex(chainSecret, chainMaterial);

                    if (!Objects.equals(recomputed, e.getEventHash())) {
                        return SensitiveAccessAuditVerificationResultDTO.failure(
                                verified,
                                e.getId(),
                                tenantId,
                                "Event hash mismatch"
                        );
                    }

                    lastHashByTenant.put(tenantId, e.getEventHash());
                    verified++;

                    cursorTs = e.getTimestamp();
                    cursorUuid = e.getId();
                }
            }
        } finally {
            // meta-audit: always record read for verify (success or failure)
            recordSensitiveAccessRead();
        }
    }

    /**
     * Meta-audit: successful read of sensitive access audit stream.
     * <p>
     * - Must run AFTER successful repository read.
     * - Uses same correlationId as request.
     * - Deterministic fingerprint.
     */
    private void recordSensitiveAccessRead() {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "SENSITIVE_ACCESS",
                actor.getId().toString(),
                rootTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "SENSITIVE_ACCESS",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read sensitive access audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
