package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessAuditEvent;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessAuditEventRepository;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessCanonicalMaterialBuilder;
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
public class SensitiveAccessAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            SensitiveAccessCanonicalMaterialBuilder.STREAM;

    private final SensitiveAccessAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final SensitiveAccessCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditRequestContextExtractor contextExtractor;

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
        CursorQueryResult<SensitiveAccessAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? SensitiveAccessAuditSpecifications.timestampFrom(from) : null,
                                to != null ? SensitiveAccessAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                                tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                                actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                                cursorTimestamp != null
                                        ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                SensitiveAccessAuditDTO::from,
                SensitiveAccessAuditEvent::getTimestamp,
                SensitiveAccessAuditEvent::getId
        );

        recordMeta("AUDIT_READ");

        return new SensitiveAccessAuditCursorPageDTO(
                result.items(),
                result.hasMore(),
                result.nextCursorTimestamp(),
                result.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(
            Instant from,
            Instant to
    ) {
        AuditVerificationResultDTO result = executeVerification(
                from,
                to,
                null,
                (effectiveFrom, cursorTimestamp, cursorId, pageable) -> repository.findAll(
                        Specification.allOf(
                                SensitiveAccessAuditSpecifications.timestampFrom(effectiveFrom),
                                SensitiveAccessAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                event -> AuditPartition.tenant(STREAM, event.getTenantId().toString()),
                event -> canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                ),
                SensitiveAccessAuditEvent::getId,
                SensitiveAccessAuditEvent::getTimestamp,
                SensitiveAccessAuditEvent::getChainVersion,
                SensitiveAccessAuditEvent::getPrevEventHash,
                SensitiveAccessAuditEvent::getEventHash,
                auditChainService
        );

        recordMeta("AUDIT_VERIFY");
        return result;
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
                                SensitiveAccessAuditSpecifications.timestampFrom(from),
                                SensitiveAccessAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? SensitiveAccessAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? SensitiveAccessAuditSpecifications.hasSubjectId(subjectId) : null,
                                tenantId != null ? SensitiveAccessAuditSpecifications.hasTenantId(tenantId) : null,
                                actorUserId != null ? SensitiveAccessAuditSpecifications.hasActorUserId(actorUserId) : null,
                                cursorTs != null
                                        ? SensitiveAccessAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
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
        executeCsvExport(
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
                this::writeCsvHeader,
                this::writeCsvLine,
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    private void writeCsvHeader(PrintWriter w) {
        w.println(String.join(",",
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
        ));
    }

    private void writeCsvLine(PrintWriter w, SensitiveAccessAuditEvent e) {
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
    }

    private void recordMeta(String action) {
        User actor = userService.getRequiredCurrentUser();
        UUID storageTenant = tenantService.getRootTenant().getId();
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
                "Sensitive access audit operation",
                SensitiveDataClassification.REGULATED
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}