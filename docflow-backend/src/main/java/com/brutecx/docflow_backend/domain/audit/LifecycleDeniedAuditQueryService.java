package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class LifecycleDeniedAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = LifecycleDeniedCanonicalMaterialBuilder.STREAM;

    private final LifecycleDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final LifecycleDeniedCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(LifecycleDeniedAuditQueryService.class);

    /* =====================================================
       CURSOR QUERY
       ===================================================== */

    @Transactional(readOnly = true)
    public LifecycleDeniedAuditCursorPageDTO query(
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

        Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                from != null ? LifecycleDeniedAuditSpecifications.timestampFrom(from) : null,
                to != null ? LifecycleDeniedAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                cursorTimestamp != null
                        ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<LifecycleDeniedAuditEvent> page = repository.findAll(spec, pageable);

        List<LifecycleDeniedAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<LifecycleDeniedAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(LifecycleDeniedAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            LifecycleDeniedAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordSensitiveAccess("AUDIT_READ");

        return new LifecycleDeniedAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY
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

        Map<AuditPartition, String> lastHashByPartition = new HashMap<>();

        while (true) {

            Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                    LifecycleDeniedAuditSpecifications.timestampFrom(from),
                    LifecycleDeniedAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<LifecycleDeniedAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) break;

            for (LifecycleDeniedAuditEvent event : batch.getContent()) {

                AuditPartition partition = resolvePartition(event);
                String previousHash = lastHashByPartition.get(partition);

                if (previousHash != null &&
                        !Objects.equals(previousHash, normalizeHash(event.getPrevEventHash()))) {

                    recordSensitiveAccess("AUDIT_VERIFY");
                    return AuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "CONTINUITY_MISMATCH_PREV_EVENT_HASH partition=" + partition.partitionValue()
                    );
                }

                if (event.getChainVersion() > 0) {

                    String canonical = canonicalMaterialBuilder.buildCanonicalMaterial(
                            canonicalMaterialBuilder.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            normalizeHash(event.getPrevEventHash()),
                            canonical
                    );

                    if (!Objects.equals(expected, normalizeHash(event.getEventHash()))) {

                        recordSensitiveAccess("AUDIT_VERIFY");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH partition=" + partition.partitionValue()
                        );
                    }
                }

                lastHashByPartition.put(partition, normalizeHash(event.getEventHash()));
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordSensitiveAccess("AUDIT_VERIFY");
        return AuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
       EXPORT JSONL + CSV
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
                        "correlationId",
                        "correlationSource",
                        "executionContext",
                        "result",
                        "subjectId",
                        "reasonCode",
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

                Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                        LifecycleDeniedAuditSpecifications.timestampFrom(from),
                        LifecycleDeniedAuditSpecifications.timestampTo(to),
                        hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                        hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                        (cursorTimestamp != null && cursorId != null)
                                ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<LifecycleDeniedAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (LifecycleDeniedAuditEvent e : page.getContent()) {

                    try {
                        if (csv) {
                            writeCsvLine(w, e);
                        } else {
                            w.println(objectMapper.writeValueAsString(
                                    LifecycleDeniedAuditForensicExportDTO.from(e)
                            ));
                        }
                    } catch (Exception ex) {
                        if (!response.isCommitted()) {
                            response.resetBuffer();
                        }
                        log.error("LifecycleDenied export failed at eventId={} csv={}", e.getId(), csv, ex);
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
            throw new IllegalStateException("Failed to stream lifecycle denied export", ex);
        }
    }

    private void writeCsvLine(PrintWriter w, LifecycleDeniedAuditEvent e) {
        w.println(String.join(",",
                csv(e.getId()),
                csv(e.getTimestamp()),
                csv(e.getCorrelationId()),
                csv(e.getCorrelationSource()),
                csv(e.getExecutionContext()),
                csv(e.getResult()),
                csv(e.getSubjectId()),
                csv(e.getReasonCode()),
                csv(e.getHttpMethod()),
                csv(e.getPath()),
                csv(e.getIp()),
                csv(e.getUserAgent()),
                csv(e.getEventFingerprint()),
                csv(e.getChainVersion()),
                csv(e.getPrevEventHash()),
                csv(e.getEventHash())
        ));
    }

    /* =====================================================
       PARTITION
       ===================================================== */

    private AuditPartition resolvePartition(LifecycleDeniedAuditEvent e) {
        String subject = e.getSubjectId();
        if (subject != null && !subject.isBlank()) {
            return AuditPartition.subject(STREAM, subject.trim());
        }
        return AuditPartition.global(STREAM);
    }

    /* =====================================================
       META
       ===================================================== */

    private void recordSensitiveAccess(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID storageTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
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
                action,
                "Lifecycle denied audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

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
