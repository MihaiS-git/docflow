package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.sensitive.*;
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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SensitiveAccessAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            SensitiveAccessCanonicalMaterialBuilder.STREAM;

    private final SensitiveAccessAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final SensitiveAccessCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

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
        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

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

        recordMeta("AUDIT_READ");

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

        return new SensitiveAccessAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {
        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

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
                    recordMeta("AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }
    }

    @Transactional
    public void streamForensicExportJsonl(
            OutputStream out,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            UUID tenantId,
            UUID actorUserId
    ) {
        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                out,
                STREAM,
                from,
                to,
                tenantId,
                EXPORT_MAX_ROWS,
                (Instant cursorTs, UUID cursorUuid) -> {

                    Pageable pageable = PageRequest.of(
                            0,
                            EXPORT_BATCH_SIZE,
                            Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                    );

                    Specification<SensitiveAccessAuditEvent> spec = Specification.allOf(
                            SensitiveAccessAuditSpecifications.timestampFrom(from),
                            SensitiveAccessAuditSpecifications.timestampTo(to),
                            hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                            hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                            tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                            actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                            cursorTs != null
                                    ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
                SensitiveAccessAuditForensicExportDTO::from,
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

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
                                SensitiveAccessAuditSpecifications.timestampFrom(from),
                                SensitiveAccessAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                                tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                                actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null
                        ),
                        pageable
                ),
                (PrintWriter w) -> w.println(String.join(",",
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
                )),
                (PrintWriter w, SensitiveAccessAuditEvent e) -> {
                    w.println(String.join(",",
                            AuditStreamSupport.csv(e.getId()),
                            AuditStreamSupport.csv(e.getTimestamp()),
                            AuditStreamSupport.csv(e.getActorUserId()),
                            AuditStreamSupport.csv(e.getActorExternalSubjectId()),
                            AuditStreamSupport.csv(e.getTenantId()),
                            AuditStreamSupport.csv(e.getSubjectType()),
                            AuditStreamSupport.csv(e.getSubjectId()),
                            AuditStreamSupport.csv(e.getResource()),
                            AuditStreamSupport.csv(e.getAction()),
                            AuditStreamSupport.csv(e.getResourcePath()),
                            AuditStreamSupport.csv(e.getCorrelationId()),
                            AuditStreamSupport.csv(e.getCorrelationSource()),
                            AuditStreamSupport.csv(e.getExecutionContext()),
                            AuditStreamSupport.csv(e.getResult()),
                            AuditStreamSupport.csv(e.getIp()),
                            AuditStreamSupport.csv(e.getUserAgent()),
                            AuditStreamSupport.csv(e.getReasonCode()),
                            AuditStreamSupport.csv(e.getReasonDetail()),
                            AuditStreamSupport.csv(e.getDataClassification()),
                            AuditStreamSupport.csv(e.getEventFingerprint()),
                            AuditStreamSupport.csv(e.getChainVersion()),
                            AuditStreamSupport.csv(e.getPrevEventHash()),
                            AuditStreamSupport.csv(e.getEventHash())
                    ));
                },
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    private void recordMeta(String action) {
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
                "Sensitive access audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}