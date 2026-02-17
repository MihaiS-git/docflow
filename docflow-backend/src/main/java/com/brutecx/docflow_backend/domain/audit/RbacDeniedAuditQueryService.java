package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEventRepository;
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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

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
    private final ObjectMapper objectMapper;

    /* =====================================================
       CURSOR QUERY – DESC timestamp, DESC id
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
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                from != null ? RbacDeniedAuditSpecifications.timestampFrom(from) : null,
                to != null ? RbacDeniedAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                cursorTimestamp != null
                        ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<RbacDeniedAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccess("AUDIT_READ");

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
       VERIFY – ASC timestamp, ASC id
       - strict continuity per partition during this scan
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

        // stateKey -> lastEventHash (from previous row in this scan)
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
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<RbacDeniedAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                recordSensitiveAccess("AUDIT_VERIFY");
                return AuditVerificationResultDTO.success(verified);
            }

            for (RbacDeniedAuditEvent event : batch.getContent()) {

                AuditPartition partition = resolvePartition(event);
                String stateKey = partition.toStateKey();

                String actualPrev = normalizeHash(event.getPrevEventHash());

                // Continuity check only after we have a previous element for this partition in-memory.
                if (lastHashByPartitionStateKey.containsKey(stateKey)) {
                    String expectedPrev = lastHashByPartitionStateKey.get(stateKey);
                    if (!Objects.equals(expectedPrev, actualPrev)) {
                        recordSensitiveAccess("AUDIT_VERIFY");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH partition=" + partition.partitionValue()
                        );
                    }
                }

                // Canonical material is the single source of truth.
                String canonical = canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                );

                // Recompute expected hash (service returns "-" when disabled/misconfigured or chainVersion<=0)
                String expectedHash = auditChainService.computeEventHash(
                        partition,
                        event.getChainVersion(),
                        actualPrev,
                        canonical
                );

                String storedHash = normalizeHash(event.getEventHash());

                if (!Objects.equals(expectedHash, storedHash)) {
                    recordSensitiveAccess("AUDIT_VERIFY");
                    return AuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "EVENT_HASH_MISMATCH_RECOMPUTED_VS_STORED partition=" + partition.partitionValue()
                    );
                }

                lastHashByPartitionStateKey.put(stateKey, storedHash);
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }
    }

    /* =====================================================
       EXPORT JSONL + CSV (ASC timestamp, ASC id)
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
    ) {
        streamExport(response, from, to, correlationId, subjectId, false);
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
    ) {
        streamExport(response, from, to, correlationId, subjectId, true);
    }

    private void streamExport(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            boolean csv
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        response.setBufferSize(16 * 1024);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            if (csv) {
                w.println(String.join(",",
                        "id",
                        "timestamp",
                        "subjectId",
                        "correlationId",
                        "correlationSource",
                        "executionContext",
                        "result",
                        "httpMethod",
                        "path",
                        "ip",
                        "userAgent",
                        "eventFingerprint",
                        "chainVersion",
                        "prevEventHash",
                        "eventHash"
                ));
                w.flush();
                response.flushBuffer();
            }

            while (true) {

                Specification<RbacDeniedAuditEvent> spec = Specification.allOf(
                        RbacDeniedAuditSpecifications.timestampFrom(from),
                        RbacDeniedAuditSpecifications.timestampTo(to),
                        hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                        hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                        (cursorTimestamp != null && cursorId != null)
                                ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<RbacDeniedAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (RbacDeniedAuditEvent e : page.getContent()) {

                    try {
                        if (csv) {
                            w.println(String.join(",",
                                    csv(e.getId()),
                                    csv(e.getTimestamp()),
                                    csv(e.getSubjectId()),
                                    csv(e.getCorrelationId()),
                                    csv(e.getCorrelationSource()),
                                    csv(e.getExecutionContext()),
                                    csv(e.getResult()),
                                    csv(e.getHttpMethod()),
                                    csv(e.getPath()),
                                    csv(e.getIp()),
                                    csv(e.getUserAgent()),
                                    csv(e.getEventFingerprint()),
                                    csv(e.getChainVersion()),
                                    csv(e.getPrevEventHash()),
                                    csv(e.getEventHash())
                            ));
                        } else {
                            w.println(objectMapper.writeValueAsString(
                                    RbacDeniedAuditForensicExportDTO.from(e)
                            ));
                        }
                    } catch (Exception ex) {
                        if (!response.isCommitted()) {
                            response.resetBuffer();
                        }
                        throw ex;
                    }

                    exported++;
                    if (exported >= EXPORT_MAX_ROWS) {
                        recordSensitiveAccess("AUDIT_EXPORT");
                        w.flush();
                        response.flushBuffer();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                w.flush();
                response.flushBuffer();

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordSensitiveAccess("AUDIT_EXPORT");
            w.flush();
            response.flushBuffer();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream RBAC denied forensic export", ex);
        }
    }

    /* =====================================================
       SENSITIVE ACCESS AUDIT
       ===================================================== */

    private void recordSensitiveAccess(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
                "SCOPE_GLOBAL",
                actor.getId().toString(),
                rootTenant.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenant,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                action,
                "RBAC denied audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    /* =====================================================
       PARTITION
       ===================================================== */

    private static AuditPartition resolvePartition(RbacDeniedAuditEvent e) {
        String subject = e.getSubjectId();
        if (subject != null && !subject.isBlank()) {
            return AuditPartition.subject(STREAM, subject.trim());
        }
        return AuditPartition.global(STREAM);
    }

    /* =====================================================
       HELPERS
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

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
