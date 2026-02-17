package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.onboarding.*;
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
public class OnboardingAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = OnboardingCanonicalMaterialBuilder.STREAM;

    private final OnboardingAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final OnboardingCanonicalMaterialBuilder canonicalBuilder;
    private final ObjectMapper objectMapper;

    /* =====================================================
       CURSOR QUERY – DESC timestamp, DESC id
       ===================================================== */

    @Transactional(readOnly = true)
    public OnboardingAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID inviteId,
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

        Specification<OnboardingAuditEvent> spec = Specification.allOf(
                from != null ? OnboardingAuditSpecifications.timestampFrom(from) : null,
                to != null ? OnboardingAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? OnboardingAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectId) ? OnboardingAuditSpecifications.hasSubjectId(subjectId) : null,
                tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                inviteId != null ? OnboardingAuditSpecifications.hasInviteId(inviteId) : null,
                cursorTimestamp != null
                        ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<OnboardingAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccess(tenantId, "AUDIT_READ");

        List<OnboardingAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<OnboardingAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(OnboardingAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            OnboardingAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        return new OnboardingAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – ASC timestamp, ASC id
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to,
            UUID tenantId
    ) {

        validateRangeRequired(from, to);

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();

        while (true) {

            Specification<OnboardingAuditEvent> spec = Specification.allOf(
                    OnboardingAuditSpecifications.timestampFrom(from),
                    OnboardingAuditSpecifications.timestampTo(to),
                    tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                    cursorTimestamp != null
                            ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<OnboardingAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
                return AuditVerificationResultDTO.success(verified);
            }

            for (OnboardingAuditEvent event : batch.getContent()) {

                UUID eventTenantId = event.getTenantId();
                if (eventTenantId == null) {
                    recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
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
                        recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH"
                        );
                    }

                    String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(
                            canonicalBuilder.fromEvent(event)
                    );

                    String expectedHash = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            canonicalMaterial
                    );

                    if (!Objects.equals(expectedHash, normalizeHash(event.getEventHash()))) {
                        recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH"
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
       FORENSIC EXPORT – JSONL
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            UUID tenantId
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            while (true) {

                Specification<OnboardingAuditEvent> spec = Specification.allOf(
                        OnboardingAuditSpecifications.timestampFrom(from),
                        OnboardingAuditSpecifications.timestampTo(to),
                        tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                        (cursorTimestamp != null && cursorId != null)
                                ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<OnboardingAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (OnboardingAuditEvent e : page.getContent()) {

                    w.println(objectMapper.writeValueAsString(
                            OnboardingAuditForensicExportDTO.from(e)
                    ));

                    exported++;
                    if (exported >= EXPORT_MAX_ROWS) {
                        recordSensitiveAccess(tenantId, "AUDIT_EXPORT");
                        w.flush();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordSensitiveAccess(tenantId, "AUDIT_EXPORT");
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream onboarding audit forensic export", ex);
        }
    }

    /* =====================================================
   FORENSIC EXPORT – CSV (ASC timestamp, ASC id)
   ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            UUID tenantId
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            // ---- CSV HEADER ----
            w.println(String.join(",",
                    "id",
                    "timestamp",
                    "actorUserId",
                    "subjectId",
                    "tenantId",
                    "inviteId",
                    "correlationId",
                    "correlationSource",
                    "executionContext",
                    "ip",
                    "userAgent",
                    "result",
                    "outcome",
                    "reasonCode",
                    "reasonDetail",
                    "eventFingerprint",
                    "chainVersion",
                    "prevEventHash",
                    "eventHash"
            ));

            while (true) {

                Specification<OnboardingAuditEvent> spec = Specification.allOf(
                        OnboardingAuditSpecifications.timestampFrom(from),
                        OnboardingAuditSpecifications.timestampTo(to),
                        tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                        (cursorTimestamp != null && cursorId != null)
                                ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<OnboardingAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (OnboardingAuditEvent e : page.getContent()) {

                    w.println(String.join(",",
                            csv(e.getId()),
                            csv(e.getTimestamp()),
                            csv(e.getActorUserId()),
                            csv(e.getSubjectId()),
                            csv(e.getTenantId()),
                            csv(e.getInviteId()),
                            csv(e.getCorrelationId()),
                            csv(e.getCorrelationSource()),
                            csv(e.getExecutionContext()),
                            csv(e.getIp()),
                            csv(e.getUserAgent()),
                            csv(e.getResult()),
                            csv(e.getOutcome()),
                            csv(e.getReasonCode()),
                            csv(e.getReasonDetail()),
                            csv(e.getEventFingerprint()),
                            csv(e.getChainVersion()),
                            csv(e.getPrevEventHash()),
                            csv(e.getEventHash())
                    ));

                    exported++;

                    if (exported >= EXPORT_MAX_ROWS) {
                        recordSensitiveAccess(tenantId, "AUDIT_EXPORT");
                        w.flush();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordSensitiveAccess(tenantId, "AUDIT_EXPORT");
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream onboarding audit CSV export", ex);
        }
    }


    /* =====================================================
       SENSITIVE READ AUDIT
       ===================================================== */

    private void recordSensitiveAccess(UUID requestedTenantId, String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();

        UUID storageTenant = tenantService.getRootTenant().getId();
        boolean tenantScoped = requestedTenantId != null;

        String scope = tenantScoped ? "SCOPE_TENANT" : "SCOPE_GLOBAL";
        UUID effectiveTenant = tenantScoped ? requestedTenantId : storageTenant;

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
                scope,
                actor.getId().toString(),
                effectiveTenant.toString(),
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
                tenantScoped
                        ? "Onboarding audit operation (tenant-scoped)"
                        : "Onboarding audit operation (global)",
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
        boolean needsQuotes =
                s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
