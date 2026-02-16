package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.*;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
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
public class CredentialLifecycleAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;
    private static final String STREAM = "CREDENTIAL_LIFECYCLE";

    private static final String PARTITION_ANON = "ANON";

    private final CredentialLifecycleAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final CredentialLifecycleCanonicalMaterialBuilder canonicalBuilder;
    private final AuditChainService auditChainService;
    private final ObjectMapper objectMapper;

    /* ================= CURSOR ================= */

    @Transactional(readOnly = true)
    public CredentialLifecycleAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectExternalId,
            AuditResult result,
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

        Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                from != null ? CredentialLifecycleAuditSpecifications.timestampFrom(from) : null,
                to != null ? CredentialLifecycleAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? CredentialLifecycleAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectExternalId) ? CredentialLifecycleAuditSpecifications.hasSubjectExternalId(subjectExternalId) : null,
                result != null ? CredentialLifecycleAuditSpecifications.hasResult(result) : null,
                cursorTimestamp != null
                        ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<CredentialLifecycleAuditEvent> page = repository.findAll(spec, pageable);

        List<CredentialLifecycleAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<CredentialLifecycleAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(CredentialLifecycleAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            CredentialLifecycleAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordMeta("AUDIT_READ");

        return new CredentialLifecycleAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* ================= VERIFY ================= */

    @Transactional(readOnly = true)
    public CredentialLifecycleAuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {

        validateRangeRequired(from, to);

        Map<AuditPartition, String> lastHashByPartition = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                    CredentialLifecycleAuditSpecifications.timestampFrom(from),
                    CredentialLifecycleAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? CredentialLifecycleAuditSpecifications.cursorAfter(
                            cursorTimestamp,
                            cursorId,
                            true
                    )
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<CredentialLifecycleAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) break;

            for (CredentialLifecycleAuditEvent event : batch.getContent()) {

                String stream = canonicalBuilder.stream();

                String subjectValue = hasText(event.getSubjectExternalId())
                        ? event.getSubjectExternalId().trim()
                        : PARTITION_ANON;

                AuditPartition partition = AuditPartition.subject(stream, subjectValue);

                String previousHash = lastHashByPartition.get(partition);

                if (previousHash != null &&
                        !Objects.equals(previousHash, normalizeHash(event.getPrevEventHash()))) {

                    recordMeta("AUDIT_VERIFY");
                    return CredentialLifecycleAuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "CONTINUITY_MISMATCH_PREV_EVENT_HASH"
                    );
                }

                String canonical = canonicalBuilder.buildCanonicalMaterial(
                        CredentialLifecycleCanonicalInput.from(event)
                );

                String expectedHash = auditChainService.computeEventHash(
                        partition,
                        event.getChainVersion(),
                        normalizeHash(event.getPrevEventHash()),
                        canonical
                );

                if (!Objects.equals(expectedHash, event.getEventHash())) {

                    recordMeta("AUDIT_VERIFY");
                    return CredentialLifecycleAuditVerificationResultDTO.failure(
                            verified,
                            event.getId(),
                            "EVENT_HASH_MISMATCH_RECOMPUTED_VS_STORED"
                    );
                }

                lastHashByPartition.put(partition, normalizeHash(event.getEventHash()));
                verified++;

                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordMeta("AUDIT_VERIFY");
        return CredentialLifecycleAuditVerificationResultDTO.success(verified);
    }

    /* ================= EXPORT JSONL ================= */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to
    ) {

        streamExport(response, from, to, false);
    }

    /* ================= EXPORT CSV ================= */

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

        try (PrintWriter w = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            while (true) {

                Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                        CredentialLifecycleAuditSpecifications.timestampFrom(from),
                        CredentialLifecycleAuditSpecifications.timestampTo(to),
                        (cursorTimestamp != null && cursorId != null)
                                ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                : null
                );

                Page<CredentialLifecycleAuditEvent> page = repository.findAll(spec, pageable);
                if (page.isEmpty()) break;

                for (CredentialLifecycleAuditEvent e : page.getContent()) {

                    if (csv) {
                        w.println(String.join(",",
                                csv(e.getId()),
                                csv(e.getTimestamp()),
                                csv(e.getSubjectExternalId()),
                                csv(e.getCorrelationId()),
                                csv(e.getChainVersion()),
                                csv(e.getPrevEventHash()),
                                csv(e.getEventHash())
                        ));
                    } else {
                        w.println(objectMapper.writeValueAsString(e));
                    }

                    exported++;
                    if (exported >= EXPORT_MAX_ROWS) {
                        recordMeta("AUDIT_EXPORT");
                        w.flush();
                        return;
                    }

                    cursorTimestamp = e.getTimestamp();
                    cursorId = e.getId();
                }

                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }

            recordMeta("AUDIT_EXPORT");
            w.flush();

        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream credential lifecycle export", ex);
        }
    }

    /* ================= META ================= */

    private void recordMeta(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
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
                "Credential lifecycle audit operation",
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
        if (!s.contains(",") && !s.contains("\"") && !s.contains("\n")) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String normalizeHash(String v) {
        return (v == null || v.isBlank()) ? "-" : v;
    }
}
