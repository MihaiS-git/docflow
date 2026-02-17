package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEventRepository;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedCanonicalInput;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class RbacDeniedAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = RbacDeniedCanonicalMaterialBuilder.STREAM;

    private final RbacDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    private final AuditChainService auditChainService;
    private final RbacDeniedCanonicalMaterialBuilder canonicalMaterialBuilder;

    /* =====================================================
       CURSOR QUERY – timestamp DESC, id DESC
       ===================================================== */

    @Transactional(readOnly = true)
    public RbacDeniedAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {

        validateRange(from, to);
        validateCursorPair(cursorTimestamp, cursorId);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(
                        Sort.Order.desc("timestamp"),
                        Sort.Order.desc("id")
                )
        );

        Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                from != null ? RbacDeniedAuditSpecifications.timestampFrom(from) : null,
                to != null ? RbacDeniedAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                cursorTimestamp != null ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false) : null
        );

        Page<RbacDeniedAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccess("READ", "Read RBAC denied audit stream (global)");

        List<RbacDeniedAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<RbacDeniedAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(RbacDeniedAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            RbacDeniedAuditEvent lastIncluded = raw.get(safeSize - 1);
            nextTs = lastIncluded.getTimestamp();
            nextId = lastIncluded.getId();
        }

        return new RbacDeniedAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – timestamp ASC, id ASC
       Strict per-partition continuity (via AuditPartition stateKey)
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {

        validateRangeRequired(from, to);

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        // Strict continuity per partition (keyed by AuditPartition stateKey)
        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();

        while (true) {

            Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                    RbacDeniedAuditSpecifications.timestampFrom(from),
                    RbacDeniedAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
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

            Page<RbacDeniedAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (global)");
                return AuditVerificationResultDTO.success(verified);
            }

            for (RbacDeniedAuditEvent event : batch.getContent()) {

                AuditPartition partition = resolvePartition(event);
                String stateKey = partition.toStateKey();

                String actualPrev = normalizeHash(event.getPrevEventHash());
                String expectedPrev = lastHashByPartitionStateKey.getOrDefault(stateKey, "-");

                if (event.getChainVersion() > 0) {

                    if (!Objects.equals(expectedPrev, actualPrev)) {
                        recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (continuity mismatch)");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH partition=" + partition.partitionValue()
                        );
                    }

                    String storedEventHash = normalizeHash(event.getEventHash());
                    if ("-".equals(storedEventHash)) {
                        recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (missing event hash)");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "MISSING_EVENT_HASH_FOR_CHAINED_EVENT partition=" + partition.partitionValue()
                        );
                    }

                    String canonical = canonicalMaterialBuilder.buildCanonicalMaterial(
                            RbacDeniedCanonicalInput.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            canonical
                    );

                    if (!Objects.equals(expected, storedEventHash)) {
                        recordSensitiveAccess("VERIFY", "Verify RBAC denied audit stream (event hash mismatch)");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH partition=" + partition.partitionValue()
                        );
                    }
                }

                lastHashByPartitionStateKey.put(stateKey, normalizeHash(event.getEventHash()));
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }
    }

    /* =====================================================
       FORENSIC EXPORT – streaming (JSONL via controller)
       ===================================================== */

    @Transactional(readOnly = true)
    public void exportForensic(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Consumer<RbacDeniedAuditForensicExportDTO> consumer
    ) {

        validateRangeRequired(from, to);
        Objects.requireNonNull(consumer, "consumer is required");

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
                    hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                    hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                    (cursorTs != null && cursorUuid != null)
                            ? RbacDeniedAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                            : null
            );

            Page<RbacDeniedAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                break;
            }

            for (RbacDeniedAuditEvent e : batch.getContent()) {

                consumer.accept(RbacDeniedAuditForensicExportDTO.from(e));

                exported++;
                if (exported >= EXPORT_MAX_ROWS) {
                    recordSensitiveAccess("EXPORT", "Export RBAC denied audit stream (row cap reached)");
                    return;
                }

                cursorTs = e.getTimestamp();
                cursorUuid = e.getId();
            }
        }

        recordSensitiveAccess("EXPORT", "Export RBAC denied audit stream (global)");
    }

    /* =====================================================
       SENSITIVE READ AUDIT
       ===================================================== */

    private void recordSensitiveAccess(String action, String detail) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();

        UUID storageTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_" + action,
                STREAM,
                "SCOPE_GLOBAL",
                actor.getId().toString(),
                storageTenant.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                storageTenant,
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

    /* =====================================================
       PARTITION (unified AuditPartition strategy)
       ===================================================== */

    private static AuditPartition resolvePartition(RbacDeniedAuditEvent e) {
        String subject = e.getSubjectId();
        if (subject != null && !subject.isBlank() && !"UNKNOWN".equals(subject.trim())) {
            return AuditPartition.subject(STREAM, subject);
        }
        return AuditPartition.global(STREAM);
    }

    /* =====================================================
       VALIDATION + HELPERS
       ===================================================== */

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be <= 'to'");
        }
    }

    private void validateRangeRequired(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        validateRange(from, to);
    }

    private void validateCursorPair(Instant ts, UUID id) {
        if ((ts == null) ^ (id == null)) {
            throw new IllegalArgumentException("cursorTimestamp and cursorId must be provided together");
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
