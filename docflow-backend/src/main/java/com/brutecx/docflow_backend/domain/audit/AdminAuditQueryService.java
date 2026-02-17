// src/main/java/com/brutecx/docflow_backend/domain/audit/AdminAuditQueryService.java
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

import java.io.PrintWriter;
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

        GoldAuditSupport.validateRange(from, to);
        GoldAuditSupport.validateCursorPair(cursorTimestamp, cursorId);

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

        recordSensitiveAccess(tenantId, "AUDIT_READ");

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
       VERIFY – timestamp ASC, id ASC
       - strict continuity per partition (tenant partition OR global partition)
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to,
            UUID tenantId
    ) {

        GoldAuditSupport.validateRangeRequired(from, to);

        long verified = 0;
        Instant cursorTimestamp = null;
        UUID cursorId = null;

        // stateKey -> lastEventHash
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
                recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
                return AuditVerificationResultDTO.success(verified);
            }

            for (AdminAuditEvent event : batch.getContent()) {

                UUID eventTenantId = event.getTenantId();
                AuditPartition partition =
                        eventTenantId != null
                                ? AuditPartition.tenant(STREAM, eventTenantId.toString())
                                : AuditPartition.global(STREAM);

                String material = canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                );

                AuditVerificationResultDTO failure = GoldAuditSupport.verifyEvent(
                        event.getId(),
                        partition,
                        event.getChainVersion(),
                        event.getPrevEventHash(),
                        event.getEventHash(),
                        material,
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
       EXPORT JSONL + CSV (ASC timestamp, ASC id)
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to,
            UUID tenantId
    ) {
        streamExport(response, from, to, tenantId, false);
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            UUID tenantId
    ) {
        streamExport(response, from, to, tenantId, true);
    }

    private void streamExport(
            HttpServletResponse response,
            Instant from,
            Instant to,
            UUID tenantId,
            boolean csv
    ) {

        GoldAuditSupport.validateRangeRequired(from, to);

        GoldAuditSupport.streamExportAsc(
                response,
                EXPORT_BATCH_SIZE,
                EXPORT_MAX_ROWS,
                pageable -> {
                    Specification<AdminAuditEvent> spec = Specification.allOf(
                            AdminAuditSpecifications.timestampFrom(from),
                            AdminAuditSpecifications.timestampTo(to),
                            tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null
                    );
                    return repository.findAll(spec, pageable);
                },
                (PrintWriter w) -> {
                    if (!csv) return;
                    w.println(String.join(",",
                            "id",
                            "timestamp",
                            "tenantId",
                            "actorUserId",
                            "subjectId",
                            "actionType",
                            "result",
                            "correlationId",
                            "correlationSource",
                            "executionContext",
                            "ip",
                            "userAgent",
                            "targetUserId",
                            "metadata",
                            "eventFingerprint",
                            "chainVersion",
                            "prevEventHash",
                            "eventHash"
                    ));
                },
                (PrintWriter w, AdminAuditEvent e) -> {
                    if (csv) {
                        writeCsvLine(w, e);
                    } else {
                        w.println(objectMapper.writeValueAsString(
                                AdminAuditForensicExportDTO.from(e)
                        ));
                    }
                },
                () -> recordSensitiveAccess(tenantId, "AUDIT_EXPORT")
        );
    }

    private void writeCsvLine(PrintWriter w, AdminAuditEvent e) {
        w.println(String.join(",",
                GoldAuditSupport.csv(e.getId()),
                GoldAuditSupport.csv(e.getTimestamp()),
                GoldAuditSupport.csv(e.getTenantId()),
                GoldAuditSupport.csv(e.getActorUserId()),
                GoldAuditSupport.csv(e.getSubjectId()),
                GoldAuditSupport.csv(e.getActionType()),
                GoldAuditSupport.csv(e.getResult()),
                GoldAuditSupport.csv(e.getCorrelationId()),
                GoldAuditSupport.csv(e.getCorrelationSource()),
                GoldAuditSupport.csv(e.getExecutionContext()),
                GoldAuditSupport.csv(e.getIp()),
                GoldAuditSupport.csv(e.getUserAgent()),
                GoldAuditSupport.csv(e.getTargetUserId()),
                GoldAuditSupport.csv(e.getMetadata() != null ? safeJson(e.getMetadata()) : ""),
                GoldAuditSupport.csv(e.getEventFingerprint()),
                GoldAuditSupport.csv(e.getChainVersion()),
                GoldAuditSupport.csv(e.getPrevEventHash()),
                GoldAuditSupport.csv(e.getEventHash())
        ));
    }

    private String safeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            // last-resort: avoid breaking export
            return String.valueOf(o);
        }
    }

    /* =====================================================
       META AUDIT (Sensitive Access)
       - keeps tenant-scoped vs global semantics
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
                        ? "Admin audit stream operation (tenant-scoped)"
                        : "Admin audit stream operation (global)",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    /* =====================================================
       HELPERS
       ===================================================== */

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
