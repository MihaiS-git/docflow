package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
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
public class RbacDeniedAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 50_000;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final String STREAM = "RBAC_DENIED";

    private final RbacDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    @Value("${docflow.audit.chain.secret:}")

    private String chainSecret;

    @Transactional(readOnly = true)
    public Page<RbacDeniedAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size,
            Sort.Direction direction
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction dir = (direction != null) ? direction : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                0,
                safeSize,
                Sort.by(
                        new Sort.Order(dir, "timestamp"),
                        new Sort.Order(dir, "id")
                )
        );

        Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                from != null ? RbacDeniedAuditSpecifications.timestampFrom(from) : null,
                to != null ? RbacDeniedAuditSpecifications.timestampTo(to) : null,
                (correlationId != null && !correlationId.isBlank())
                        ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                (subjectId != null && !subjectId.isBlank())
                        ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId)
                        : null,
                (cursorTimestamp != null && cursorId != null)
                        ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, dir == Sort.Direction.ASC)
                        : null
        );

        Page<RbacDeniedAuditEvent> result = repository.findAll(spec, pageable);

        recordSensitiveAccess("READ", "Query RBAC denied audit stream");

        return result.map(RbacDeniedAuditDTO::from);

    }

    @Transactional(readOnly = true)
    public void export(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Consumer<RbacDeniedAuditEvent> consumer
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to required");
        }

        long exported = 0;
        Instant cursorTs = null;
        UUID cursorUuid = null;

        while (true) {
            Pageable pageable = PageRequest.of(
                    0,
                    EXPORT_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                    RbacDeniedAuditSpecifications.timestampFrom(from),
                    RbacDeniedAuditSpecifications.timestampTo(to),
                    (correlationId != null && !correlationId.isBlank())
                            ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId)
                            : null,
                    (subjectId != null && !subjectId.isBlank())
                            ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId)
                            : null,
                    (cursorTs != null && cursorUuid != null)
                            ? RbacDeniedAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                            : null
            );

            Page<RbacDeniedAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                break;
            }

            for (RbacDeniedAuditEvent e : batch.getContent()) {
                consumer.accept(e);
                exported++;

                if (exported >= EXPORT_MAX_ROWS) {
                    recordSensitiveAccess("EXPORT", "Export RBAC denied audit stream (row cap reached)");
                    return;
                }

                cursorTs = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess("EXPORT", "Export RBAC denied audit stream");
    }

    @Transactional(readOnly = true)
    public RbacDeniedAuditVerificationResultDTO verify(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to required");
        }

        if (chainSecret == null || chainSecret.isBlank()) {
            recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (missing chain secret)");
            return RbacDeniedAuditVerificationResultDTO.failure(
                    0,
                    null,
                    "Audit chain secret missing (docflow.audit.chain.secret)"
            );
        }

        // Partitioned by subjectId (or RBAC_DENIED_GLOBAL if subjectId == UNKNOWN)
        Map<String, String> lastHashByPartition = new HashMap<>();

        long verified = 0;
        Instant cursorTs = null;
        UUID cursorUuid = null;

        while (true) {
            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                    RbacDeniedAuditSpecifications.timestampFrom(from),
                    RbacDeniedAuditSpecifications.timestampTo(to),
                    (cursorTs != null && cursorUuid != null)
                            ? RbacDeniedAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                            : null
            );

            Page<RbacDeniedAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                break;
            }

            for (RbacDeniedAuditEvent e : batch.getContent()) {
                String resolvedSubject = (e.getSubjectId() != null && !e.getSubjectId().isBlank())
                        ? e.getSubjectId()
                        : "UNKNOWN";

                String partitionKey =
                        !"UNKNOWN".equals(resolvedSubject)
                                ? resolvedSubject
                                : STREAM + "_GLOBAL";

                String expectedPrev = lastHashByPartition.get(partitionKey);
                if (expectedPrev != null && !Objects.equals(e.getPrevEventHash(), expectedPrev)) {
                    recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (continuity mismatch)");
                    return RbacDeniedAuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "Chain continuity mismatch partitionKey=" + partitionKey +
                                    " expectedPrev=" + expectedPrev +
                                    " actualPrev=" + e.getPrevEventHash()
                    );
                }

                // EXACT canonical eventMaterial (matches RbacDeniedAuditServiceImpl)
                String eventMaterial = String.join("|",
                        STREAM,
                        resolvedSubject,
                        normalizeOr(e.getHttpMethod(), "UNKNOWN"),
                        normalizeOr(e.getPath(), "UNKNOWN"),
                        normalizeOr(e.getCorrelationId(), "UNKNOWN"),
                        normalizeOr(e.getEventFingerprint(), "UNKNOWN")
                );

                // EXACT chain material (matches AuditChainService)
                String chainMaterial = "v1|" + STREAM + "|" + partitionKey + "|" +
                        normalizeOr(e.getPrevEventHash(), "-") + "|" + eventMaterial;

                String recomputed = AuditChainHasher.hmacSha256Hex(chainSecret, chainMaterial);
                if (!Objects.equals(recomputed, e.getEventHash())) {
                    recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (hash mismatch)");
                    return RbacDeniedAuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "Event hash mismatch partitionKey=" + partitionKey
                    );
                }

                lastHashByPartition.put(partitionKey, e.getEventHash());
                verified++;

                cursorTs = e.getTimestamp();
                cursorUuid = e.getId();
            }

        }

        recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream");
        return RbacDeniedAuditVerificationResultDTO.success(verified);
    }

    private static String normalizeOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    private void recordSensitiveAccess(String action, String detail) {
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_" + action,
                STREAM,
                actor.getId().toString(),
                tenantService.getRootTenant().getId().toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenantService.getRootTenant().getId(),
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_" + action,
                detail,
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }
}
