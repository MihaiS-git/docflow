package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.sensitive.*;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.unauth.*;
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
public class UnauthenticatedAccessAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            UnauthenticatedAccessCanonicalMaterialBuilder.STREAM;

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final UnauthenticatedAccessCanonicalMaterialBuilder canonicalBuilder;

    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

    /* =====================================================
       CURSOR QUERY – DESC
       ===================================================== */

    @Transactional(readOnly = true)
    public UnauthenticatedAccessAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
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

        Specification<UnauthenticatedAccessAuditEvent> spec = Specification.allOf(
                from != null ? UnauthenticatedAccessAuditSpecifications.timestampFrom(from) : null,
                to != null ? UnauthenticatedAccessAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId)
                        ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                cursorTimestamp != null
                        ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<UnauthenticatedAccessAuditEvent> page = repository.findAll(spec, pageable);

        recordMeta("AUDIT_READ");

        List<UnauthenticatedAccessAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<UnauthenticatedAccessAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(UnauthenticatedAccessAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            UnauthenticatedAccessAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        return new UnauthenticatedAccessAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – GLOBAL PARTITION (UNIFIED)
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartition = new HashMap<>();
        long verified = 0;

        Instant cursorTs = null;
        UUID cursorId = null;

        while (true) {

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Specification<UnauthenticatedAccessAuditEvent> spec = Specification.allOf(
                    UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                    UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                    cursorTs != null
                            ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                            : null
            );

            Page<UnauthenticatedAccessAuditEvent> batch = repository.findAll(spec, pageable);

            if (batch.isEmpty()) {
                recordMeta("AUDIT_VERIFY");
                return AuditVerificationResultDTO.success(verified);
            }

            for (UnauthenticatedAccessAuditEvent e : batch.getContent()) {

                AuditPartition partition = AuditPartition.global(STREAM);

                String canonical =
                        canonicalBuilder.buildCanonicalMaterial(
                                canonicalBuilder.fromEvent(e)
                        );

                AuditVerificationResultDTO failure =
                        AuditStreamSupport.verifyEvent(
                                e.getId(),
                                partition,
                                e.getChainVersion(),
                                e.getPrevEventHash(),
                                e.getEventHash(),
                                canonical,
                                auditChainService,
                                lastHashByPartition,
                                verified
                        );

                if (failure != null) {
                    recordMeta("AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTs = e.getTimestamp();
                cursorId = e.getId();
            }
        }
    }

    /* =====================================================
       SEALED JSONL EXPORT
       ===================================================== */

    @Transactional
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId
    ) {

        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                response,
                STREAM,
                from,
                to,
                null,
                EXPORT_MAX_ROWS,
                (Instant cursorTs, UUID cursorUuid) -> {

                    Pageable pageable = PageRequest.of(
                            0,
                            EXPORT_BATCH_SIZE,
                            Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                    );

                    Specification<UnauthenticatedAccessAuditEvent> spec = Specification.allOf(
                            UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                            UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                            hasText(correlationId)
                                    ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                                    : null,
                            cursorTs != null
                                    ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
                UnauthenticatedAccessAuditForensicExportDTO::from,
                () -> recordMeta("AUDIT_EXPORT")
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
            String correlationId
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
                                UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                                UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                                hasText(correlationId)
                                        ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                                        : null
                        ),
                        pageable
                ),
                (PrintWriter w) -> w.println(String.join(",",
                        "id","timestamp","httpMethod","path","ip","userAgent",
                        "correlationId","chainVersion","prevEventHash","eventHash"
                )),
                (PrintWriter w, UnauthenticatedAccessAuditEvent e) -> {
                    w.println(String.join(",",
                            AuditStreamSupport.csv(e.getId()),
                            AuditStreamSupport.csv(e.getTimestamp()),
                            AuditStreamSupport.csv(e.getHttpMethod()),
                            AuditStreamSupport.csv(e.getPath()),
                            AuditStreamSupport.csv(e.getIp()),
                            AuditStreamSupport.csv(e.getUserAgent()),
                            AuditStreamSupport.csv(e.getCorrelationId()),
                            AuditStreamSupport.csv(e.getChainVersion()),
                            AuditStreamSupport.csv(e.getPrevEventHash()),
                            AuditStreamSupport.csv(e.getEventHash())
                    ));
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
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
                "SCOPE_GLOBAL",
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
                "Unauthenticated access audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}