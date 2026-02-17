package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.AuthenticationCanonicalInput;
import com.brutecx.docflow_backend.audit.auth.AuthenticationCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventRepository;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
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
public class AuthenticationAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 2_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = AuthenticationCanonicalMaterialBuilder.STREAM;

    /**
     * Partition must be identity-stable for audit integrity.
     * Preferred: subjectId (external user id).
     * Fallbacks (deterministic): username, then ANON.
     * Note: We prefix values to avoid collisions between subjectId and username strings.
     */
    private static final String PARTITION_ANON = "ANON";

    private final AuthenticationEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final AuthenticationCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final ObjectMapper objectMapper;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationAuditQueryService.class);


    /* =====================================================
       CURSOR QUERY – timestamp DESC, id DESC
       ===================================================== */

    @Transactional(readOnly = true)
    public AuthenticationAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String username,
            String subjectId,
            String resultRaw,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {

        validateRange(from, to);
        validateCursorPair(cursorTimestamp, cursorId);

        AuthenticationResult result = parseResult(resultRaw);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Specification<AuthenticationEvent> spec = Specification.allOf(
                from != null ? AuthenticationAuditSpecifications.timestampFrom(from) : null,
                to != null ? AuthenticationAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? AuthenticationAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(username) ? AuthenticationAuditSpecifications.hasUsername(username) : null,
                hasText(subjectId) ? AuthenticationAuditSpecifications.hasSubjectId(subjectId) : null,
                result != null ? AuthenticationAuditSpecifications.hasResult(result) : null,
                cursorTimestamp != null
                        ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<AuthenticationEvent> page = repository.findAll(spec, pageable);

        List<AuthenticationEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<AuthenticationAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(AuthenticationAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            AuthenticationEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordMeta("AUDIT_READ");

        return new AuthenticationAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – timestamp ASC, id ASC
       Partition continuity: per subject boundary
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        validateRangeRequired(from, to);

        Map<AuditPartition, String> lastHashByPartition = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<AuthenticationEvent> spec = Specification.allOf(
                    AuthenticationAuditSpecifications.timestampFrom(from),
                    AuthenticationAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<AuthenticationEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) break;

            for (AuthenticationEvent e : batch.getContent()) {

                AuditPartition partition = resolvePartition(e);
                String previousHash = lastHashByPartition.get(partition);

                // 1) Strict continuity per partition
                if (previousHash != null &&
                        !Objects.equals(previousHash, normalizeHash(e.getPrevEventHash()))) {

                    recordMeta("AUDIT_VERIFY");
                    return AuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "CONTINUITY_MISMATCH_PREV_EVENT_HASH partition=" + partition.partitionValue()
                    );
                }

                // 2) Canonical material
                AuthenticationCanonicalInput input = new AuthenticationCanonicalInput(
                        e.getTimestamp().toEpochMilli(),
                        e.getSource(),
                        e.getUsername(),
                        e.getSubjectId(),
                        e.getResult(),
                        e.getIdp(),
                        e.getIp(),
                        e.getUserAgent(),
                        e.getCorrelationId(),
                        e.getCorrelationSource(),
                        e.getExecutionContext(),
                        e.getAuditResult(),
                        e.getEventFingerprint()
                );

                String canonicalMaterial = canonicalMaterialBuilder.buildCanonicalMaterial(input);

                // 3) Recompute hash (partition aligned!)
                String expectedHash = auditChainService.computeEventHash(
                        partition,
                        e.getChainVersion(),
                        normalizeHash(e.getPrevEventHash()),
                        canonicalMaterial
                );

                if (!Objects.equals(expectedHash, e.getEventHash())) {

                    recordMeta("AUDIT_VERIFY");
                    return AuditVerificationResultDTO.failure(
                            verified,
                            e.getId(),
                            "EVENT_HASH_MISMATCH_RECOMPUTED_VS_STORED partition=" + partition.partitionValue()
                    );
                }

                // 4) Advance partition chain
                lastHashByPartition.put(partition, normalizeHash(e.getEventHash()));
                verified++;

                cursorTimestamp = e.getTimestamp();
                cursorId = e.getId();
            }
        }

        recordMeta("AUDIT_VERIFY");
        return AuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
       EXPORT JSONL + CSV (ASC timestamp, ASC id)
       Export hashing is unchanged; this only streams data.
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to
    ) {
        streamExport(response, from, to, false);
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to
    ) {
        streamExport(response, from, to, true);
    }

    private void streamExport(
            HttpServletResponse response,
            Instant from,
            Instant to,
            boolean csv
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        // Smaller buffer helps flush earlier through proxies/clients.
        response.setBufferSize(16 * 1024);

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            if (csv) {
                w.println(String.join(",",
                        "id",
                        "timestamp",
                        "source",
                        "username",
                        "subjectId",
                        "result",
                        "idp",
                        "ip",
                        "userAgent",
                        "correlationId",
                        "correlationSource",
                        "executionContext",
                        "auditResult",
                        "eventFingerprint",
                        "chainVersion",
                        "prevEventHash",
                        "eventHash"
                ));
                w.flush();
                response.flushBuffer();
            }

            while (true) {

                Specification<AuthenticationEvent> spec = Specification.allOf(
                        AuthenticationAuditSpecifications.timestampFrom(from),
                        AuthenticationAuditSpecifications.timestampTo(to),
                        (cursorTimestamp != null && cursorId != null)
                                ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<AuthenticationEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (AuthenticationEvent e : page.getContent()) {

                    try {
                        if (csv) {
                            writeCsvLine(w, e);
                        } else {
                            AuthenticationAuditForensicExportDTO dto = AuthenticationAuditForensicExportDTO.from(e);
                            w.println(objectMapper.writeValueAsString(dto));
                        }
                    } catch (Exception ex) {
                        // If we haven't committed yet, reset so the client gets a proper 500 instead of a broken download.
                        if (!response.isCommitted()) {
                            response.resetBuffer();
                        }
                        log.error("Authentication export failed at eventId={} csv={}", e.getId(), csv, ex);
                        throw ex;
                    }

                    exported++;
                    if (exported >= EXPORT_MAX_ROWS) {
                        recordMeta("AUDIT_EXPORT");
                        w.flush();
                        response.flushBuffer();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                // Critical for long downloads: flush each batch so proxies/clients see progress.
                w.flush();
                response.flushBuffer();

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordMeta("AUDIT_EXPORT");
            w.flush();
            response.flushBuffer();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream authentication export", ex);
        }
    }

    private void writeCsvLine(PrintWriter w, AuthenticationEvent e) {
        w.println(String.join(",",
                csv(e.getId()),
                csv(e.getTimestamp()),
                csv(e.getSource()),
                csv(e.getUsername()),
                csv(e.getSubjectId()),
                csv(e.getResult()),
                csv(e.getIdp()),
                csv(e.getIp()),
                csv(e.getUserAgent()),
                csv(e.getCorrelationId()),
                csv(e.getCorrelationSource()),
                csv(e.getExecutionContext()),
                csv(e.getAuditResult()),
                csv(e.getEventFingerprint()),
                csv(e.getChainVersion()),
                csv(e.getPrevEventHash()),
                csv(e.getEventHash())
        ));
    }

    /* =====================================================
       META AUDIT
       ===================================================== */

    private void recordMeta(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
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
                "Audit stream operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    /* =====================================================
       PARTITION RESOLUTION (BEST-PRACTICE)
       ===================================================== */

    private AuditPartition resolvePartition(AuthenticationEvent e) {
        if (hasText(e.getSubjectId())) {
            return AuditPartition.subject(STREAM, e.getSubjectId().trim());
        }
        if (hasText(e.getUsername())) {
            return AuditPartition.subject(STREAM, e.getUsername().trim().toLowerCase(Locale.ROOT));
        }
        return AuditPartition.subject(STREAM, PARTITION_ANON);
    }

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    /* =====================================================
       VALIDATION UTIL
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

    private AuthenticationResult parseResult(String raw) {
        if (!hasText(raw)) return null;
        return AuthenticationResult.valueOf(raw.trim().toUpperCase(Locale.ROOT));
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
}
