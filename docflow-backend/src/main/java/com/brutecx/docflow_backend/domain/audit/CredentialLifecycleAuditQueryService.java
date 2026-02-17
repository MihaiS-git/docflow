package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEventRepository;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleCanonicalMaterialBuilder;
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

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CredentialLifecycleAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = CredentialLifecycleCanonicalMaterialBuilder.STREAM;
    private static final String PARTITION_ANON = "ANON";

    private final CredentialLifecycleAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final CredentialLifecycleCanonicalMaterialBuilder canonicalBuilder;
    private final AuditChainService auditChainService;
    private final ObjectMapper objectMapper;

    /* =====================================================
       CURSOR QUERY – DESC
       ===================================================== */

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

        GoldAuditSupport.validateRange(from, to);
        GoldAuditSupport.validateCursorPair(cursorTimestamp, cursorId);

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

    /* =====================================================
       VERIFY – ASC
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        GoldAuditSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                    CredentialLifecycleAuditSpecifications.timestampFrom(from),
                    CredentialLifecycleAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
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

                AuditPartition partition = resolvePartition(event);

                String canonical = canonicalBuilder.buildCanonicalMaterial(
                        canonicalBuilder.fromEvent(event)
                );

                AuditVerificationResultDTO failure =
                        GoldAuditSupport.verifyEvent(
                                event.getId(),
                                partition,
                                event.getChainVersion(),
                                event.getPrevEventHash(),
                                event.getEventHash(),
                                canonical,
                                auditChainService,
                                lastHashByPartitionStateKey,
                                verified
                        );

                if (failure != null) {
                    recordMeta("AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordMeta("AUDIT_VERIFY");
        return AuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
       EXPORT – ASC
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

        GoldAuditSupport.validateRangeRequired(from, to);

        GoldAuditSupport.streamExportAsc(
                response,
                EXPORT_BATCH_SIZE,
                EXPORT_MAX_ROWS,
                pageable -> repository.findAll(
                        Specification.allOf(
                                CredentialLifecycleAuditSpecifications.timestampFrom(from),
                                CredentialLifecycleAuditSpecifications.timestampTo(to)
                        ),
                        pageable
                ),
                csv
                        ? w -> w.println(String.join(",",
                        "id",
                        "timestamp",
                        "subjectExternalId",
                        "clientId",
                        "sessionId",
                        "ip",
                        "eventType",
                        "requiredAction",
                        "correlationId",
                        "correlationSource",
                        "executionContext",
                        "result",
                        "reasonCode",
                        "reasonDetail",
                        "eventFingerprint",
                        "chainVersion",
                        "prevEventHash",
                        "eventHash"
                ))
                        : null,
                (w, e) -> {
                    if (csv) {
                        w.println(String.join(",",
                                GoldAuditSupport.csv(e.getId()),
                                GoldAuditSupport.csv(e.getTimestamp()),
                                GoldAuditSupport.csv(e.getSubjectExternalId()),
                                GoldAuditSupport.csv(e.getClientId()),
                                GoldAuditSupport.csv(e.getSessionId()),
                                GoldAuditSupport.csv(e.getIp()),
                                GoldAuditSupport.csv(e.getEventType()),
                                GoldAuditSupport.csv(e.getRequiredAction()),
                                GoldAuditSupport.csv(e.getCorrelationId()),
                                GoldAuditSupport.csv(e.getCorrelationSource()),
                                GoldAuditSupport.csv(e.getExecutionContext()),
                                GoldAuditSupport.csv(e.getResult()),
                                GoldAuditSupport.csv(e.getReasonCode()),
                                GoldAuditSupport.csv(e.getReasonDetail()),
                                GoldAuditSupport.csv(e.getEventFingerprint()),
                                GoldAuditSupport.csv(e.getChainVersion()),
                                GoldAuditSupport.csv(e.getPrevEventHash()),
                                GoldAuditSupport.csv(e.getEventHash())
                        ));
                    } else {
                        w.println(objectMapper.writeValueAsString(
                                CredentialLifecycleAuditForensicExportDTO.from(e)
                        ));
                    }
                },
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    /* =====================================================
       META
       ===================================================== */

    private void recordMeta(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
                "SCOPE_GLOBAL",
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

    private AuditPartition resolvePartition(CredentialLifecycleAuditEvent e) {
        String subjectValue = hasText(e.getSubjectExternalId())
                ? e.getSubjectExternalId().trim()
                : PARTITION_ANON;

        return AuditPartition.subject(STREAM, subjectValue);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
