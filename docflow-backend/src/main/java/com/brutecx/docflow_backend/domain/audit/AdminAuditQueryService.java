package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.admin.AdminAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEventRepository;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
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
public class AdminAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = AdminAuditCanonicalMaterialBuilder.STREAM;

    private final AdminAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final AdminAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final ObjectMapper objectMapper;

    /* =====================================================
       CURSOR QUERY – DESC timestamp, DESC id
       ===================================================== */

    @Transactional(readOnly = true)
    public AdminAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            UUID actorUserId,
            UUID tenantId,
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

        Specification<AdminAuditEvent> spec = Specification.allOf(
                from != null ? AdminAuditSpecifications.timestampFrom(from) : null,
                to != null ? AdminAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? AdminAuditSpecifications.hasCorrelationId(correlationId) : null,
                actorUserId != null ? AdminAuditSpecifications.hasActorUserId(actorUserId) : null,
                tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null,
                cursorTimestamp != null
                        ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<AdminAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccess(tenantId);

        List<AdminAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<AdminAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(AdminAuditDTO.from(raw.get(i)));
        }

        Instant nextCursorTs = null;
        UUID nextCursorId = null;
        if (hasMore) {
            AdminAuditEvent lastIncluded = raw.get(safeSize - 1);
            nextCursorTs = lastIncluded.getTimestamp();
            nextCursorId = lastIncluded.getId();
        }

        return new AdminAuditCursorPageDTO(items, hasMore, nextCursorTs, nextCursorId);
    }

    /* =====================================================
       VERIFY – Strict per-tenant partition
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

            Specification<AdminAuditEvent> spec = Specification.allOf(
                    AdminAuditSpecifications.timestampFrom(from),
                    AdminAuditSpecifications.timestampTo(to),
                    tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null,
                    cursorTimestamp != null
                            ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<AdminAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                recordSensitiveAccess(tenantId);
                return AuditVerificationResultDTO.success(verified);
            }

            for (AdminAuditEvent event : batch.getContent()) {

                UUID eventTenantId = event.getTenantId();
                AuditPartition partition =
                        eventTenantId != null
                                ? AuditPartition.tenant(STREAM, eventTenantId.toString())
                                : AuditPartition.global(STREAM);

                String stateKey = partition.toStateKey();
                String actualPrev = normalizeHash(event.getPrevEventHash());
                String expectedPrev = lastHashByPartitionStateKey.getOrDefault(stateKey, "-");

                if (event.getChainVersion() > 0) {

                    if (!Objects.equals(expectedPrev, actualPrev)) {
                        recordSensitiveAccess(tenantId);
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH"
                        );
                    }

                    String storedHash = normalizeHash(event.getEventHash());
                    if ("-".equals(storedHash)) {
                        recordSensitiveAccess(tenantId);
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "MISSING_EVENT_HASH_FOR_CHAINED_EVENT"
                        );
                    }

                    String material = canonicalMaterialBuilder.buildCanonicalMaterial(
                            canonicalMaterialBuilder.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            material
                    );

                    if (!Objects.equals(expected, storedHash)) {
                        recordSensitiveAccess(tenantId);
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

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            while (true) {

                Specification<AdminAuditEvent> spec = Specification.allOf(
                        AdminAuditSpecifications.timestampFrom(from),
                        AdminAuditSpecifications.timestampTo(to),
                        tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null,
                        (cursorTimestamp != null && cursorId != null)
                                ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<AdminAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (AdminAuditEvent e : page.getContent()) {

                    w.println(objectMapper.writeValueAsString(
                            AdminAuditForensicExportDTO.from(e)
                    ));

                    exported++;
                    if (exported >= EXPORT_MAX_ROWS) {
                        recordSensitiveAccess(tenantId);
                        w.flush();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordSensitiveAccess(tenantId);
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream admin audit forensic export", ex);
        }
    }

    /* =====================================================
       SENSITIVE READ AUDIT
       ===================================================== */

    private void recordSensitiveAccess(UUID requestedTenantId) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();

        UUID storageTenant = tenantService.getRootTenant().getId();
        boolean tenantScoped = requestedTenantId != null;

        String scope = tenantScoped ? "SCOPE_TENANT" : "SCOPE_GLOBAL";
        UUID effectiveTenant = tenantScoped ? requestedTenantId : storageTenant;

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
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
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                tenantScoped
                        ? "Read admin audit stream (tenant-scoped)"
                        : "Read admin audit stream (global)",
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
}
