package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.unauth.*;
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
public class UnauthenticatedAccessAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = UnauthenticatedAccessCanonicalMaterialBuilder.STREAM;

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final UnauthenticatedAccessCanonicalMaterialBuilder canonicalBuilder;

    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final ObjectMapper objectMapper;

    /* =====================================================
       CURSOR QUERY – DESC
       ===================================================== */

    @Transactional(readOnly = true)
    public UnauthenticatedAccessAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
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

        Specification<UnauthenticatedAccessAuditEvent> spec = Specification.allOf(
                from != null ? UnauthenticatedAccessAuditSpecifications.timestampFrom(from) : null,
                to != null ? UnauthenticatedAccessAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId)
                        ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                cursorTimestamp != null
                        ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<UnauthenticatedAccessAuditEvent> page = repository.findAll(spec, pageable);

        List<UnauthenticatedAccessAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<UnauthenticatedAccessAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(UnauthenticatedAccessAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            UnauthenticatedAccessAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordMeta("AUDIT_READ", buildScope("QUERY", from, to, correlationId));

        return new UnauthenticatedAccessAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – GLOBAL PARTITION
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        validateRangeRequired(from, to);

        long verified = 0;
        Instant cursorTs = null;
        UUID cursorId = null;

        AuditPartition partition = AuditPartition.global(STREAM);
        String lastHash = "-";

        while (true) {

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Specification<UnauthenticatedAccessAuditEvent> spec = Specification.allOf(
                    UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                    UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                    cursorTs != null
                            ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                            : null
            );

            Page<UnauthenticatedAccessAuditEvent> batch = repository.findAll(spec, pageable);

            if (batch.isEmpty()) {
                recordMeta("AUDIT_VERIFY", buildScope("VERIFY", from, to, null));
                return AuditVerificationResultDTO.success(verified);
            }

            for (UnauthenticatedAccessAuditEvent e : batch.getContent()) {

                String prev = normalizeHash(e.getPrevEventHash());
                String current = normalizeHash(e.getEventHash());

                if (!Objects.equals(lastHash, prev)) {
                    recordMeta("AUDIT_VERIFY", buildScope("VERIFY_CONTINUITY_MISMATCH", from, to, null));
                    return AuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "CONTINUITY_MISMATCH_PREV_EVENT_HASH"
                    );
                }

                if (e.getChainVersion() > 0) {

                    String canonical = canonicalBuilder.buildCanonicalMaterial(
                            canonicalBuilder.fromEvent(e)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            e.getChainVersion(),
                            prev,
                            canonical
                    );

                    if (!Objects.equals(expected, current)) {
                        recordMeta("AUDIT_VERIFY", buildScope("VERIFY_EVENT_HASH_MISMATCH", from, to, null));
                        return AuditVerificationResultDTO.failure(
                                verified,
                                e.getId(),
                                "EVENT_HASH_MISMATCH_RECOMPUTED_VS_STORED"
                        );
                    }
                }

                lastHash = current;
                verified++;

                cursorTs = e.getTimestamp();
                cursorId = e.getId();
            }
        }
    }

    /* =====================================================
       EXPORT – JSONL + CSV
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId
    ) {
        streamExport(response, from, to, correlationId, false);
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId
    ) {
        streamExport(response, from, to, correlationId, true);
    }

    private void streamExport(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            boolean csv
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTs = null;
        UUID cursorId = null;

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            if (csv) {
                w.println(String.join(",",
                        "id",
                        "timestamp",
                        "httpMethod",
                        "path",
                        "ip",
                        "userAgent",
                        "correlationId",
                        "chainVersion",
                        "prevEventHash",
                        "eventHash"
                ));
            }

            while (true) {

                Pageable pageable = PageRequest.of(
                        0,
                        EXPORT_BATCH_SIZE,
                        Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                );

                Specification<UnauthenticatedAccessAuditEvent> spec = Specification.allOf(
                        UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                        UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                        hasText(correlationId)
                                ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                                : null,
                        cursorTs != null
                                ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                                : null
                );

                Page<UnauthenticatedAccessAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (UnauthenticatedAccessAuditEvent e : page.getContent()) {

                    if (csv) {
                        w.println(String.join(",",
                                csv(e.getId()),
                                csv(e.getTimestamp()),
                                csv(e.getHttpMethod()),
                                csv(e.getPath()),
                                csv(e.getIp()),
                                csv(e.getUserAgent()),
                                csv(e.getCorrelationId()),
                                csv(e.getChainVersion()),
                                csv(e.getPrevEventHash()),
                                csv(e.getEventHash())
                        ));
                    } else {
                        w.println(objectMapper.writeValueAsString(
                                UnauthenticatedAccessAuditForensicExportDTO.from(e)
                        ));
                    }

                    exported++;
                    if (exported >= EXPORT_MAX_ROWS) {
                        recordMeta("AUDIT_EXPORT", buildScope("EXPORT_CAP", from, to, correlationId));
                        w.flush();
                        return;
                    }

                    cursorTs = e.getTimestamp();
                    cursorId = e.getId();
                }
            }

            recordMeta("AUDIT_EXPORT", buildScope("EXPORT", from, to, correlationId));
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream unauthenticated access forensic export", ex);
        }
    }

    /* =====================================================
       META
       ===================================================== */

    private void recordMeta(String action, String scope) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                action,
                scope,
                actor.getId().toString(),
                rootTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                action,
                scope,
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private String buildScope(String op, Instant from, Instant to, String correlationId) {
        return String.join(";",
                "op=" + op,
                "from=" + (from == null ? "" : from),
                "to=" + (to == null ? "" : to),
                "correlationId=" + (correlationId == null ? "" : correlationId)
        );
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

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (!s.contains(",") && !s.contains("\"") && !s.contains("\n")) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
