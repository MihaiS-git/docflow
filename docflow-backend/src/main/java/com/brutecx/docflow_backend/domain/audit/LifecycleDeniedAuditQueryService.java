package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.lifecycle.*;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.domain.audit.export.SealedJsonlAuditExportService;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LifecycleDeniedAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            LifecycleDeniedCanonicalMaterialBuilder.STREAM;

    private final LifecycleDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final LifecycleDeniedCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

    /* =====================================================
       CURSOR QUERY – DESC
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

        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

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

        List<LifecycleDeniedAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<LifecycleDeniedAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(LifecycleDeniedAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            LifecycleDeniedAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordSensitiveAccess("AUDIT_READ");

        return new LifecycleDeniedAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – ASC
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

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
            if (batch.isEmpty()) break;

            for (LifecycleDeniedAuditEvent event : batch.getContent()) {

                AuditPartition partition = resolvePartition(event);

                String canonical =
                        canonicalMaterialBuilder.buildCanonicalMaterial(
                                canonicalMaterialBuilder.fromEvent(event)
                        );

                AuditVerificationResultDTO failure =
                        AuditStreamSupport.verifyEvent(
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
                    recordSensitiveAccess("AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordSensitiveAccess("AUDIT_VERIFY");
        return AuditVerificationResultDTO.success(verified);
    }

    /* =====================================================
       SEALED JSONL EXPORT
       ===================================================== */

    @Transactional
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
    ) {

        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                response,
                STREAM,
                from,
                to,
                null,
                EXPORT_MAX_ROWS,
                (Instant cursorTs, UUID cursorId) -> {

                    Pageable pageable = PageRequest.of(
                            0,
                            EXPORT_BATCH_SIZE,
                            Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                    );

                    Specification<LifecycleDeniedAuditEvent> spec = Specification.allOf(
                            LifecycleDeniedAuditSpecifications.timestampFrom(from),
                            LifecycleDeniedAuditSpecifications.timestampTo(to),
                            hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                            hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                            cursorTs != null
                                    ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
                LifecycleDeniedAuditForensicExportDTO::from,
                () -> recordSensitiveAccess("AUDIT_EXPORT")
        );
    }

    /* =====================================================
       CSV EXPORT
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
    ) {

        AuditStreamSupport.validateRangeRequired(from, to);

        AuditStreamSupport.streamExportCsvAsc(
                response,
                STREAM,
                from,
                to,
                EXPORT_BATCH_SIZE,
                EXPORT_MAX_ROWS,
                pageable -> repository.findAll(
                        Specification.allOf(
                                LifecycleDeniedAuditSpecifications.timestampFrom(from),
                                LifecycleDeniedAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null
                        ),
                        pageable
                ),
                (PrintWriter w) -> w.println(String.join(",",
                        "id",
                        "timestamp",
                        "correlationId",
                        "correlationSource",
                        "executionContext",
                        "result",
                        "subjectId",
                        "reasonCode",
                        "httpMethod",
                        "path",
                        "ip",
                        "userAgent",
                        "eventFingerprint",
                        "chainVersion",
                        "prevEventHash",
                        "eventHash"
                )),
                (PrintWriter w, LifecycleDeniedAuditEvent e) -> {
                    w.println(String.join(",",
                            AuditStreamSupport.csv(e.getId()),
                            AuditStreamSupport.csv(e.getTimestamp()),
                            AuditStreamSupport.csv(e.getCorrelationId()),
                            AuditStreamSupport.csv(e.getCorrelationSource()),
                            AuditStreamSupport.csv(e.getExecutionContext()),
                            AuditStreamSupport.csv(e.getResult()),
                            AuditStreamSupport.csv(e.getSubjectId()),
                            AuditStreamSupport.csv(e.getReasonCode()),
                            AuditStreamSupport.csv(e.getHttpMethod()),
                            AuditStreamSupport.csv(e.getPath()),
                            AuditStreamSupport.csv(e.getIp()),
                            AuditStreamSupport.csv(e.getUserAgent()),
                            AuditStreamSupport.csv(e.getEventFingerprint()),
                            AuditStreamSupport.csv(e.getChainVersion()),
                            AuditStreamSupport.csv(e.getPrevEventHash()),
                            AuditStreamSupport.csv(e.getEventHash())
                    ));
                },
                () -> recordSensitiveAccess("AUDIT_EXPORT")
        );
    }

    /* =====================================================
       PARTITION
       ===================================================== */

    private AuditPartition resolvePartition(LifecycleDeniedAuditEvent e) {
        if (hasText(e.getSubjectId())) {
            return AuditPartition.subject(STREAM, e.getSubjectId().trim());
        }
        return AuditPartition.global(STREAM);
    }

    /* =====================================================
       META
       ===================================================== */

    private void recordSensitiveAccess(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID storageTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
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
                action,
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                action,
                "Lifecycle denied audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}