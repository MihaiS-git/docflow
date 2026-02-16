package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEventRepository;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedCanonicalInput;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedCanonicalMaterialBuilder;
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

        recordSensitiveAccess();

        List<LifecycleDeniedAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<LifecycleDeniedAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(LifecycleDeniedAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            LifecycleDeniedAuditEvent lastIncluded = raw.get(safeSize - 1);
            nextTs = lastIncluded.getTimestamp();
            nextId = lastIncluded.getId();
        }

        return new LifecycleDeniedAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY
       ===================================================== */

    @Transactional(readOnly = true)
    public LifecycleDeniedAuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {

        validateRangeRequired(from, to);

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Map<String, String> lastHashByPartition = new HashMap<>();

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
            if (batch.isEmpty()) {
                recordSensitiveAccess();
                return LifecycleDeniedAuditVerificationResultDTO.success(verified);
            }

            for (LifecycleDeniedAuditEvent event : batch.getContent()) {

                AuditPartition partition = resolvePartition(event);
                String stateKey = partition.toStateKey();

                String actualPrev = normalizeHash(event.getPrevEventHash());
                String expectedPrev = lastHashByPartition.getOrDefault(stateKey, "-");

                if (event.getChainVersion() > 0 && !Objects.equals(expectedPrev, actualPrev)) {
                    recordSensitiveAccess();
                    return LifecycleDeniedAuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "CONTINUITY_MISMATCH_PREV_EVENT_HASH partition=" + partition.partitionValue()
                    );
                }

                if (event.getChainVersion() > 0) {

                    String canonical = canonicalMaterialBuilder.buildCanonicalMaterial(
                            LifecycleDeniedCanonicalInput.fromEvent(event)
                    );

                    String expected = auditChainService.computeEventHash(
                            partition,
                            event.getChainVersion(),
                            actualPrev,
                            canonical
                    );

                    if (!Objects.equals(expected, normalizeHash(event.getEventHash()))) {
                        recordSensitiveAccess();
                        return LifecycleDeniedAuditVerificationResultDTO.failure(
                                verified,
                                event.getId(),
                                "EVENT_HASH_MISMATCH partition=" + partition.partitionValue()
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
       FORENSIC EXPORT
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
    ) {

        validateRangeRequired(from, to);

        long exported = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        Sort sortAsc = Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(0, EXPORT_BATCH_SIZE, sortAsc);

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

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

                    LifecycleDeniedAuditForensicExportDTO dto =
                            LifecycleDeniedAuditForensicExportDTO.from(e);

                    w.println(objectMapper.writeValueAsString(dto));
                    exported++;

                    if (exported >= EXPORT_MAX_ROWS) {
                        recordSensitiveAccess();
                        w.flush();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordSensitiveAccess();
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream lifecycle denied forensic export", ex);
        }
    }

    /* =====================================================
       PARTITION
       ===================================================== */

    private static AuditPartition resolvePartition(LifecycleDeniedAuditEvent e) {
        String subject = e.getSubjectId();
        if (subject != null && !subject.isBlank()) {
            return AuditPartition.subject(STREAM, subject);
        }
        return AuditPartition.global(STREAM);
    }

    /* =====================================================
       SENSITIVE READ
       ===================================================== */

    private void recordSensitiveAccess() {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID storageTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
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
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read lifecycle denied audit stream (global)",
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
