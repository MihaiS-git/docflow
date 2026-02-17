package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEventRepository;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingCanonicalInput;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingCanonicalMaterialBuilder;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
       CURSOR QUERY – timestamp DESC, id DESC
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
                cursorTimestamp != null ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false) : null
        );

        Page<OnboardingAuditEvent> page = repository.findAll(spec, pageable);

        recordSensitiveAccess(tenantId);

        List<OnboardingAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<OnboardingAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(OnboardingAuditDTO.from(raw.get(i)));
        }

        Instant nextCursorTs = null;
        UUID nextCursorId = null;
        if (hasMore) {
            OnboardingAuditEvent lastIncluded = raw.get(safeSize - 1);
            nextCursorTs = lastIncluded.getTimestamp();
            nextCursorId = lastIncluded.getId();
        }

        return new OnboardingAuditCursorPageDTO(items, hasMore, nextCursorTs, nextCursorId);
    }

    /* =====================================================
       VERIFY – timestamp ASC, id ASC
       Strict per-partition continuity (tenant partition via AuditPartition stateKey)
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

        // Strict continuity per tenant partition (keyed by AuditPartition stateKey)
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
                recordSensitiveAccess(tenantId);
                return AuditVerificationResultDTO.success(verified);
            }

            for (OnboardingAuditEvent event : batch.getContent()) {

                UUID eventTenantId = event.getTenantId();
                if (eventTenantId == null) {
                    recordSensitiveAccess(tenantId);
                    return AuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "MISSING_TENANT_ID"
                    );
                }

                AuditPartition partition = AuditPartition.tenant(STREAM, eventTenantId.toString());
                String stateKey = partition.toStateKey();

                String actualPrev = normalizeHash(event.getPrevEventHash());
                String expectedPrev = lastHashByPartitionStateKey.getOrDefault(stateKey, "-");

                if (event.getChainVersion() > 0) {

                    if (!Objects.equals(expectedPrev, actualPrev)) {
                        recordSensitiveAccess(tenantId);
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "CONTINUITY_MISMATCH_PREV_EVENT_HASH tenantId=" + eventTenantId
                        );
                    }

                    String storedEventHash = normalizeHash(event.getEventHash());
                    if ("-".equals(storedEventHash)) {
                        recordSensitiveAccess(tenantId);
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "MISSING_EVENT_HASH_FOR_CHAINED_EVENT tenantId=" + eventTenantId
                        );
                    }

                    String canonicalMaterial = canonicalBuilder.buildCanonicalMaterial(
                            OnboardingCanonicalInput.fromEvent(event)
                    );

                    String expectedHash = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            canonicalMaterial
                    );

                    if (!Objects.equals(expectedHash, storedEventHash)) {
                        recordSensitiveAccess(tenantId);
                        return AuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH tenantId=" + eventTenantId
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
       FORENSIC EXPORT – JSONL (ASC timestamp, ASC id)
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
                    w.println(objectMapper.writeValueAsString(OnboardingAuditForensicExportDTO.from(e)));
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
            throw new IllegalStateException("Failed to stream onboarding audit forensic export", ex);
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
                        ? "Read onboarding audit stream (tenant-scoped)"
                        : "Read onboarding audit stream (global)",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
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
