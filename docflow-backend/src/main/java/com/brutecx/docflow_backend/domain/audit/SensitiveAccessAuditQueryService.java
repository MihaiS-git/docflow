package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
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
       CURSOR QUERY – timestamp DESC, id DESC
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
                        ? SensitiveAccessAuditSpecifications.cursor(
                        cursorTimestamp,
                        cursorId,
                        SensitiveAccessAuditSpecifications.SortDirection.DESC
                )
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
            SensitiveAccessAuditEvent lastIncluded = raw.get(safeSize - 1);
            nextTs = lastIncluded.getTimestamp();
            nextId = lastIncluded.getId();
        }

        recordMeta(
                "AUDIT_READ",
                buildScope("QUERY", from, to, correlationId, subjectId, tenantId, actorUserId)
        );

        return new SensitiveAccessAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – timestamp ASC, id ASC
       Strict per-tenant partition via AuditPartition
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

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();

        while (true) {

            Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                    SensitiveAccessAuditSpecifications.timestampFrom(from),
                    SensitiveAccessAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? SensitiveAccessAuditSpecifications.cursor(
                            cursorTimestamp,
                            cursorId,
                            SensitiveAccessAuditSpecifications.SortDirection.ASC
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<SensitiveAccessAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) break;

            for (SensitiveAccessAuditEvent event : batch.getContent()) {

                UUID eventTenantId = event.getTenantId();
                if (eventTenantId == null) {
                    recordMeta("AUDIT_VERIFY", buildScope("VERIFY_MISSING_TENANT", from, to, null, null, null, null));
                    return AuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "MISSING_TENANT_ID"
                    );
                }

                AuditPartition partition =
                        AuditPartition.tenant(STREAM, eventTenantId.toString());

                String stateKey = partition.toStateKey();

                String actualPrev = normalizeHash(event.getPrevEventHash());
                String expectedPrev = lastHashByPartitionStateKey.getOrDefault(stateKey, "-");

                if (event.getChainVersion() > 0) {

                    if (!Objects.equals(expectedPrev, actualPrev)) {
                        recordMeta("AUDIT_VERIFY", buildScope("VERIFY", from, to, null, null, null, null));
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH"
                        );
                    }

                    String storedHash = normalizeHash(event.getEventHash());
                    if ("-".equals(storedHash)) {
                        recordMeta("AUDIT_VERIFY", buildScope("VERIFY", from, to, null, null, null, null));
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "MISSING_EVENT_HASH_FOR_CHAINED_EVENT"
                        );
                    }

                    String canonical = canonicalMaterialBuilder.buildCanonicalMaterial(
                            SensitiveAccessCanonicalInput.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            canonical
                    );

                    if (!Objects.equals(expected, storedHash)) {
                        recordMeta("AUDIT_VERIFY", buildScope("VERIFY", from, to, null, null, null, null));
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH_RECOMPUTED_VS_STORED"
                        );
                    }
                }

                lastHashByPartitionStateKey.put(stateKey, normalizeHash(event.getEventHash()));
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordMeta("AUDIT_VERIFY", buildScope("VERIFY", from, to, null, null, null, null));
        return AuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
   FORENSIC EXPORT – CSV
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

            // CSV header
            w.println(String.join(",",
                    "id",
                    "timestamp",
                    "tenantId",
                    "actorUserId",
                    "subjectId",
                    "action",
                    "resource",
                    "result",
                    "correlationId",
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
                                ? SensitiveAccessAuditSpecifications.cursor(
                                cursorTs,
                                cursorUuid,
                                SensitiveAccessAuditSpecifications.SortDirection.ASC
                        )
                                : null
                );

                Page<SensitiveAccessAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (SensitiveAccessAuditEvent e : page.getContent()) {

                    w.println(String.join(",",
                            csv(e.getId()),
                            csv(e.getTimestamp()),
                            csv(e.getTenantId()),
                            csv(e.getActorUserId()),
                            csv(e.getSubjectId()),
                            csv(e.getAction()),
                            csv(e.getResource()),
                            csv(e.getResult()),
                            csv(e.getCorrelationId()),
                            csv(e.getChainVersion()),
                            csv(e.getPrevEventHash()),
                            csv(e.getEventHash())
                    ));

                    exported++;

                    if (exported >= EXPORT_MAX_ROWS) {
                        recordMeta("AUDIT_EXPORT",
                                buildScope("EXPORT_CSV_CAP", from, to, correlationId, subjectId, tenantId, actorUserId));
                        w.flush();
                        return;
                    }

                    cursorTs = e.getTimestamp();
                    cursorUuid = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordMeta("AUDIT_EXPORT",
                    buildScope("EXPORT_CSV", from, to, correlationId, subjectId, tenantId, actorUserId));
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream sensitive access forensic export (CSV)", ex);
        }
    }


    /* =====================================================
       FORENSIC EXPORT – JSONL (unchanged logic)
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

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            while (true) {

                Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                        SensitiveAccessAuditSpecifications.timestampFrom(from),
                        SensitiveAccessAuditSpecifications.timestampTo(to),
                        hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                        hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                        tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                        actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                        (cursorTs != null && cursorUuid != null)
                                ? SensitiveAccessAuditSpecifications.cursor(
                                cursorTs,
                                cursorUuid,
                                SensitiveAccessAuditSpecifications.SortDirection.ASC
                        )
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
                        recordMeta("AUDIT_EXPORT",
                                buildScope("EXPORT_JSONL_CAP", from, to, correlationId, subjectId, tenantId, actorUserId));
                        w.flush();
                        return;
                    }

                    cursorTs = e.getTimestamp();
                    cursorUuid = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordMeta("AUDIT_EXPORT",
                    buildScope("EXPORT_JSONL", from, to, correlationId, subjectId, tenantId, actorUserId));
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream sensitive access forensic export (JSONL)", ex);
        }
    }

    /* =====================================================
       META AUDIT
       ===================================================== */

    private void recordMeta(String action, String scope) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID storageTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                STREAM,
                action,
                scope,
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
                scope,
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private String buildScope(
            String op,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId
    ) {
        return String.join(";",
                "op=" + op,
                "from=" + (from == null ? "" : from.toString()),
                "to=" + (to == null ? "" : to.toString()),
                "correlationId=" + (correlationId == null ? "" : correlationId),
                "subjectId=" + (subjectId == null ? "" : subjectId),
                "tenantId=" + (tenantId == null ? "" : tenantId),
                "actorUserId=" + (actorUserId == null ? "" : actorUserId)
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

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (!s.contains(",") && !s.contains("\"") && !s.contains("\n") && !s.contains("\r")) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

}
