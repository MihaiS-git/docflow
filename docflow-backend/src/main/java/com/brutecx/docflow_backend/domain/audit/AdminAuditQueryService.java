package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.admin.AdminAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainCheckpoint;
import com.brutecx.docflow_backend.audit.tamper.AuditChainCheckpointRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = AdminAuditCanonicalMaterialBuilder.STREAM;

    private final AdminAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final AdminAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditChainCheckpointRepository checkpointRepository;
    private final AuditRequestContextExtractor contextExtractor;

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
        String normalizedCorrelationId = correlationId != null ? correlationId.trim() : null;

        CursorQueryResult<AdminAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? AdminAuditSpecifications.timestampFrom(from) : null,
                                to != null ? AdminAuditSpecifications.timestampTo(to) : null,
                                hasText(normalizedCorrelationId)
                                        ? AdminAuditSpecifications.hasCorrelationId(normalizedCorrelationId)
                                        : null,
                                actorUserId != null ? AdminAuditSpecifications.hasActorUserId(actorUserId) : null,
                                tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null,
                                cursorTimestamp != null
                                        ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                AdminAuditDTO::from,
                AdminAuditEvent::getTimestamp,
                AdminAuditEvent::getId
        );

        recordSensitiveAccess(tenantId, "AUDIT_READ");

        return new AdminAuditCursorPageDTO(
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
        Optional<AuditChainCheckpoint> checkpoint = checkpointRepository.findByStream(STREAM);
        Instant checkpointStart = AuditStreamSupport.resolveCheckpointStart(checkpoint);

        AuditVerificationResultDTO result = executeVerification(
                from,
                to,
                checkpointStart,
                (effectiveFrom, cursorTimestamp, cursorId, pageable) -> repository.findAll(
                        Specification.allOf(
                                AdminAuditSpecifications.timestampFrom(effectiveFrom),
                                AdminAuditSpecifications.timestampTo(to),
                                tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null,
                                cursorTimestamp != null
                                        ? AdminAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                this::resolvePartition,
                event -> canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                ),
                AdminAuditEvent::getId,
                AdminAuditEvent::getTimestamp,
                AdminAuditEvent::getChainVersion,
                AdminAuditEvent::getPrevEventHash,
                AdminAuditEvent::getEventHash,
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
                (cursorTs, cursorId) -> repository.findAll(
                        Specification.allOf(
                                AdminAuditSpecifications.timestampFrom(from),
                                AdminAuditSpecifications.timestampTo(to),
                                tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null,
                                cursorTs != null
                                        ? AdminAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
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
                AdminAuditForensicExportDTO::from,
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
                                AdminAuditSpecifications.timestampFrom(from),
                                AdminAuditSpecifications.timestampTo(to),
                                tenantId != null ? AdminAuditSpecifications.hasTenantId(tenantId) : null
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
                "eventFingerprint",
                "chainVersion",
                "prevEventHash",
                "eventHash"
        ));
    }

    private void writeCsvLine(PrintWriter w, AdminAuditEvent e) {
        w.println(String.join(",",
                AuditStreamSupport.csv(e.getId()),
                AuditStreamSupport.csv(e.getTimestamp()),
                AuditStreamSupport.csv(e.getTenantId()),
                AuditStreamSupport.csv(e.getActorUserId()),
                AuditStreamSupport.csv(e.getSubjectId()),
                AuditStreamSupport.csv(e.getActionType()),
                AuditStreamSupport.csv(e.getResult()),
                AuditStreamSupport.csv(e.getCorrelationId()),
                AuditStreamSupport.csv(e.getCorrelationSource()),
                AuditStreamSupport.csv(e.getExecutionContext()),
                AuditStreamSupport.csv(e.getIp()),
                AuditStreamSupport.csv(e.getUserAgent()),
                AuditStreamSupport.csv(e.getTargetUserId()),
                AuditStreamSupport.csv(e.getEventFingerprint()),
                AuditStreamSupport.csv(e.getChainVersion()),
                AuditStreamSupport.csv(e.getPrevEventHash()),
                AuditStreamSupport.csv(e.getEventHash())
        ));
    }

    private AuditPartition resolvePartition(AdminAuditEvent event) {
        UUID eventTenantId = event.getTenantId();

        return eventTenantId != null
                ? AuditPartition.tenant(STREAM, eventTenantId.toString())
                : AuditPartition.global(STREAM);
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
                        ? "Admin audit stream operation (tenant-scoped)"
                        : "Admin audit stream operation (global)",
                SensitiveDataClassification.REGULATED
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}