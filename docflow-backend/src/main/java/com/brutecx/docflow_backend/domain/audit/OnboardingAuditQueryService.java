package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.onboarding.*;
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

import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OnboardingAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            OnboardingCanonicalMaterialBuilder.STREAM;

    private final OnboardingAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final OnboardingCanonicalMaterialBuilder canonicalBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

    /* =====================================================
       CURSOR QUERY – DESC
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

        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
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
       VERIFY – ASC (UNIFIED)
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to,
            UUID tenantId
    ) {

        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

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

                AuditPartition partition =
                        AuditPartition.tenant(STREAM, event.getTenantId().toString());

                String canonical =
                        canonicalBuilder.buildCanonicalMaterial(
                                canonicalBuilder.fromEvent(event)
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
                    recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
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
            UUID tenantId
    ) {

        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                response,
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

                    Specification<OnboardingAuditEvent> spec = Specification.allOf(
                            OnboardingAuditSpecifications.timestampFrom(from),
                            OnboardingAuditSpecifications.timestampTo(to),
                            tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                            cursorTs != null
                                    ? OnboardingAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
                OnboardingAuditForensicExportDTO::from,
                () -> recordSensitiveAccess(tenantId, "AUDIT_EXPORT")
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
            UUID tenantId
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
                                OnboardingAuditSpecifications.timestampFrom(from),
                                OnboardingAuditSpecifications.timestampTo(to),
                                tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null
                        ),
                        pageable
                ),
                (PrintWriter w) -> w.println(String.join(",",
                        "id","timestamp","actorUserId","subjectId","tenantId",
                        "inviteId","correlationId","correlationSource","executionContext",
                        "ip","userAgent","result","outcome","reasonCode","reasonDetail",
                        "eventFingerprint","chainVersion","prevEventHash","eventHash"
                )),
                (PrintWriter w, OnboardingAuditEvent e) -> {
                    w.println(String.join(",",
                            AuditStreamSupport.csv(e.getId()),
                            AuditStreamSupport.csv(e.getTimestamp()),
                            AuditStreamSupport.csv(e.getActorUserId()),
                            AuditStreamSupport.csv(e.getSubjectId()),
                            AuditStreamSupport.csv(e.getTenantId()),
                            AuditStreamSupport.csv(e.getInviteId()),
                            AuditStreamSupport.csv(e.getCorrelationId()),
                            AuditStreamSupport.csv(e.getCorrelationSource()),
                            AuditStreamSupport.csv(e.getExecutionContext()),
                            AuditStreamSupport.csv(e.getIp()),
                            AuditStreamSupport.csv(e.getUserAgent()),
                            AuditStreamSupport.csv(e.getResult()),
                            AuditStreamSupport.csv(e.getOutcome()),
                            AuditStreamSupport.csv(e.getReasonCode()),
                            AuditStreamSupport.csv(e.getReasonDetail()),
                            AuditStreamSupport.csv(e.getEventFingerprint()),
                            AuditStreamSupport.csv(e.getChainVersion()),
                            AuditStreamSupport.csv(e.getPrevEventHash()),
                            AuditStreamSupport.csv(e.getEventHash())
                    ));
                },
                () -> recordSensitiveAccess(tenantId, "AUDIT_EXPORT")
        );
    }

    /* =====================================================
       SENSITIVE META
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

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}