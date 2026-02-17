package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.sensitive.*;
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
public class SensitiveAccessAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = SensitiveAccessCanonicalMaterialBuilder.STREAM;

    private final SensitiveAccessAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final SensitiveAccessCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final ObjectMapper objectMapper;

    /* =====================================================
       CURSOR QUERY
       ===================================================== */

    @Transactional(readOnly = true)
    public SensitiveAccessAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId,
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

        Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                from != null ? SensitiveAccessAuditSpecifications.timestampFrom(from) : null,
                to != null ? SensitiveAccessAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                cursorTimestamp != null
                        ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<SensitiveAccessAuditEvent> page = repository.findAll(spec, pageable);

        List<SensitiveAccessAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<SensitiveAccessAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(SensitiveAccessAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            SensitiveAccessAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordMeta("AUDIT_READ");

        return new SensitiveAccessAuditCursorPageDTO(items, hasMore, nextTs, nextId);
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

        Map<String, String> lastHashByPartition = new HashMap<>();

        while (true) {

            Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                    SensitiveAccessAuditSpecifications.timestampFrom(from),
                    SensitiveAccessAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<SensitiveAccessAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                recordMeta("AUDIT_VERIFY");
                return AuditVerificationResultDTO.success(verified);
            }

            for (SensitiveAccessAuditEvent event : batch.getContent()) {

                AuditPartition partition =
                        AuditPartition.tenant(STREAM, event.getTenantId().toString());

                String stateKey = partition.toStateKey();

                String actualPrev = normalizeHash(event.getPrevEventHash());
                String expectedPrev = lastHashByPartition.getOrDefault(stateKey, "-");

                if (event.getChainVersion() > 0) {

                    if (!Objects.equals(expectedPrev, actualPrev)) {
                        recordMeta("AUDIT_VERIFY");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH"
                        );
                    }

                    String canonical = canonicalMaterialBuilder.buildCanonicalMaterial(
                            canonicalMaterialBuilder.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            canonical
                    );

                    if (!Objects.equals(expected, normalizeHash(event.getEventHash()))) {
                        recordMeta("AUDIT_VERIFY");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH"
                        );
                    }
                }

                lastHashByPartition.put(stateKey, normalizeHash(event.getEventHash()));
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }
    }

    /* =====================================================
       JSONL EXPORT
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTs = null;
        UUID cursorUuid = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            while (true) {

                Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                        SensitiveAccessAuditSpecifications.timestampFrom(from),
                        SensitiveAccessAuditSpecifications.timestampTo(to),
                        hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                        hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                        tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                        actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                        (cursorTs != null && cursorUuid != null)
                                ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                                : null
                );

                Page<SensitiveAccessAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (SensitiveAccessAuditEvent e : page.getContent()) {

                    w.println(objectMapper.writeValueAsString(
                            SensitiveAccessAuditForensicExportDTO.from(e)
                    ));

                    exported++;

                    if (exported >= EXPORT_MAX_ROWS) {
                        recordMeta("AUDIT_EXPORT");
                        w.flush();
                        return;
                    }

                    cursorTs = e.getTimestamp();
                    cursorUuid = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordMeta("AUDIT_EXPORT");
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream sensitive access forensic export", ex);
        }
    }

    /* =====================================================
   CSV EXPORT – ASC timestamp, ASC id
   ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTs = null;
        UUID cursorUuid = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            // CSV HEADER (Forensic-grade, includes chain fields)
            w.println(String.join(",",
                    "id",
                    "timestamp",
                    "actorUserId",
                    "actorExternalSubjectId",
                    "tenantId",
                    "subjectType",
                    "subjectId",
                    "resource",
                    "action",
                    "resourcePath",
                    "correlationId",
                    "correlationSource",
                    "executionContext",
                    "result",
                    "ip",
                    "userAgent",
                    "reasonCode",
                    "reasonDetail",
                    "dataClassification",
                    "eventFingerprint",
                    "chainVersion",
                    "prevEventHash",
                    "eventHash"
            ));

            while (true) {

                Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                        SensitiveAccessAuditSpecifications.timestampFrom(from),
                        SensitiveAccessAuditSpecifications.timestampTo(to),
                        hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                        hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                        tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                        actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                        (cursorTs != null && cursorUuid != null)
                                ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                                : null
                );

                Page<SensitiveAccessAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (SensitiveAccessAuditEvent e : page.getContent()) {

                    w.println(String.join(",",
                            csv(e.getId()),
                            csv(e.getTimestamp()),
                            csv(e.getActorUserId()),
                            csv(e.getActorExternalSubjectId()),
                            csv(e.getTenantId()),
                            csv(e.getSubjectType()),
                            csv(e.getSubjectId()),
                            csv(e.getResource()),
                            csv(e.getAction()),
                            csv(e.getResourcePath()),
                            csv(e.getCorrelationId()),
                            csv(e.getCorrelationSource()),
                            csv(e.getExecutionContext()),
                            csv(e.getResult()),
                            csv(e.getIp()),
                            csv(e.getUserAgent()),
                            csv(e.getReasonCode()),
                            csv(e.getReasonDetail()),
                            csv(e.getDataClassification()),
                            csv(e.getEventFingerprint()),
                            csv(e.getChainVersion()),
                            csv(e.getPrevEventHash()),
                            csv(e.getEventHash())
                    ));

                    exported++;

                    if (exported >= EXPORT_MAX_ROWS) {
                        recordMeta("AUDIT_EXPORT");
                        w.flush();
                        return;
                    }

                    cursorTs = e.getTimestamp();
                    cursorUuid = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordMeta("AUDIT_EXPORT");
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream sensitive access forensic export (CSV)", ex);
        }
    }


    /* =====================================================
       META
       ===================================================== */

    private void recordMeta(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID storageTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                action,
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
                "Sensitive access audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
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
        if (!s.contains(",") && !s.contains("\"") && !s.contains("\n") && !s.contains("\r")) {
            return s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

}
