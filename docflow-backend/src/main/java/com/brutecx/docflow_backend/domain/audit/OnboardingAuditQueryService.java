package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEventRepository;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingCanonicalMaterialBuilder;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            OnboardingCanonicalMaterialBuilder.STREAM;

    private final OnboardingAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final OnboardingCanonicalMaterialBuilder canonicalBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditRequestContextExtractor contextExtractor;

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
        CursorQueryResult<OnboardingAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? OnboardingAuditSpecifications.timestampFrom(from) : null,
                                to != null ? OnboardingAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId) ? OnboardingAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? OnboardingAuditSpecifications.hasSubjectId(subjectId) : null,
                                tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                                inviteId != null ? OnboardingAuditSpecifications.hasInviteId(inviteId) : null,
                                cursorTimestamp != null
                                        ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                OnboardingAuditDTO::from,
                OnboardingAuditEvent::getTimestamp,
                OnboardingAuditEvent::getId
        );

        recordSensitiveAccess(tenantId, "AUDIT_READ");

        return new OnboardingAuditCursorPageDTO(
                result.items(),
                result.hasMore(),
                result.nextCursorTimestamp(),
                result.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to,
            UUID tenantId
    ) {
        AuditVerificationResultDTO result = executeVerification(
                from,
                to,
                null,
                (effectiveFrom, cursorTimestamp, cursorId, pageable) -> repository.findAll(
                        Specification.allOf(
                                OnboardingAuditSpecifications.timestampFrom(effectiveFrom),
                                OnboardingAuditSpecifications.timestampTo(to),
                                tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                                cursorTimestamp != null
                                        ? OnboardingAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                event -> AuditPartition.tenant(STREAM, event.getTenantId().toString()),
                event -> canonicalBuilder.buildCanonicalMaterial(
                        canonicalBuilder.fromEvent(event)
                ),
                OnboardingAuditEvent::getId,
                OnboardingAuditEvent::getTimestamp,
                OnboardingAuditEvent::getChainVersion,
                OnboardingAuditEvent::getPrevEventHash,
                OnboardingAuditEvent::getEventHash,
                auditChainService
        );

        recordSensitiveAccess(tenantId, "AUDIT_VERIFY");
        return result;
    }

    @Transactional
    public void streamForensicExportJsonl(
            OutputStream out,
            Instant from,
            Instant to,
            UUID tenantId
    ) {
        executeJsonlExport(
                out,
                STREAM,
                from,
                to,
                tenantId,
                EXPORT_MAX_ROWS,
                sealedJsonlAuditExportService,
                (cursorTs, cursorUuid) -> repository.findAll(
                        Specification.allOf(
                                OnboardingAuditSpecifications.timestampFrom(from),
                                OnboardingAuditSpecifications.timestampTo(to),
                                tenantId != null ? OnboardingAuditSpecifications.hasTenantId(tenantId) : null,
                                cursorTs != null
                                        ? OnboardingAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
                                        : null
                        ),
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                EXPORT_BATCH_SIZE,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Order.asc("timestamp"),
                                        org.springframework.data.domain.Sort.Order.asc("id")
                                )
                        )
                ),
                OnboardingAuditForensicExportDTO::from,
                () -> recordSensitiveAccess(tenantId, "AUDIT_EXPORT")
        );
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            UUID tenantId
    ) {
        executeCsvExport(
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
                this::writeCsvHeader,
                this::writeCsvLine,
                () -> recordSensitiveAccess(tenantId, "AUDIT_EXPORT")
        );
    }

    private void writeCsvHeader(PrintWriter w) {
        w.println(String.join(",",
                "id",
                "timestamp",
                "actorUserId",
                "subjectId",
                "tenantId",
                "inviteId",
                "correlationId",
                "correlationSource",
                "executionContext",
                "ip",
                "userAgent",
                "result",
                "outcome",
                "reasonCode",
                "reasonDetail",
                "eventFingerprint",
                "chainVersion",
                "prevEventHash",
                "eventHash"
        ));
    }

    private void writeCsvLine(PrintWriter w, OnboardingAuditEvent e) {
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
    }

    private void recordSensitiveAccess(UUID requestedTenantId, String action) {
        User actor = userService.getRequiredCurrentUser();
        UUID storageTenant = tenantService.getRootTenant().getId();
        boolean tenantScoped = requestedTenantId != null;
        String resourcePath = contextExtractor.fromCurrentRequest().resourcePath();

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                storageTenant,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                resourcePath,
                action,
                tenantScoped
                        ? "Onboarding audit operation (tenant-scoped)"
                        : "Onboarding audit operation (global)",
                SensitiveDataClassification.REGULATED
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}