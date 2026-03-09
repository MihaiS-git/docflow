package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.*;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.rbac.*;
import com.brutecx.docflow_backend.audit.sensitive.*;
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
public class RbacDeniedAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            RbacDeniedCanonicalMaterialBuilder.STREAM;

    private final RbacDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final RbacDeniedCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditRequestContextExtractor contextExtractor;

    @Transactional(readOnly = true)
    public RbacDeniedAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {

        CursorQueryResult<RbacDeniedAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? RbacDeniedAuditSpecifications.timestampFrom(from) : null,
                                to != null ? RbacDeniedAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                                cursorTimestamp != null
                                        ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                RbacDeniedAuditDTO::from,
                RbacDeniedAuditEvent::getTimestamp,
                RbacDeniedAuditEvent::getId
        );

        recordSensitiveAccess("AUDIT_READ");

        return new RbacDeniedAuditCursorPageDTO(
                result.items(),
                result.hasMore(),
                result.nextCursorTimestamp(),
                result.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        AuditVerificationResultDTO result = executeVerification(
                from,
                to,
                null,
                (effectiveFrom, cursorTimestamp, cursorId, pageable) -> repository.findAll(
                        Specification.allOf(
                                RbacDeniedAuditSpecifications.timestampFrom(effectiveFrom),
                                RbacDeniedAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? RbacDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                event -> resolvePartition(event),
                event -> canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                ),
                RbacDeniedAuditEvent::getId,
                RbacDeniedAuditEvent::getTimestamp,
                RbacDeniedAuditEvent::getChainVersion,
                RbacDeniedAuditEvent::getPrevEventHash,
                RbacDeniedAuditEvent::getEventHash,
                auditChainService
        );

        recordSensitiveAccess("AUDIT_VERIFY");
        return result;
    }

    @Transactional
    public void streamForensicExportJsonl(
            OutputStream out,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
    ) {

        executeJsonlExport(
                out,
                STREAM,
                from,
                to,
                null,
                EXPORT_MAX_ROWS,
                sealedJsonlAuditExportService,
                (cursorTs, cursorUuid) -> repository.findAll(
                        Specification.allOf(
                                RbacDeniedAuditSpecifications.timestampFrom(from),
                                RbacDeniedAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                                cursorTs != null
                                        ? RbacDeniedAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
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
                RbacDeniedAuditForensicExportDTO::from,
                () -> recordSensitiveAccess("AUDIT_EXPORT")
        );
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId,
            String subjectId
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
                                RbacDeniedAuditSpecifications.timestampFrom(from),
                                RbacDeniedAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? RbacDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? RbacDeniedAuditSpecifications.hasSubjectId(subjectId) : null
                        ),
                        pageable
                ),
                this::writeCsvHeader,
                this::writeCsvLine,
                () -> recordSensitiveAccess("AUDIT_EXPORT")
        );
    }

    private void writeCsvHeader(PrintWriter w) {
        w.println("id,timestamp,subjectId,correlationId,correlationSource,executionContext,result,httpMethod,path,ip,userAgent,eventFingerprint,chainVersion,prevEventHash,eventHash");
    }

    private void writeCsvLine(PrintWriter w, RbacDeniedAuditEvent e) {
        w.println(String.join(",",
                AuditStreamSupport.csv(e.getId()),
                AuditStreamSupport.csv(e.getTimestamp()),
                AuditStreamSupport.csv(e.getSubjectId()),
                AuditStreamSupport.csv(e.getCorrelationId()),
                AuditStreamSupport.csv(e.getCorrelationSource()),
                AuditStreamSupport.csv(e.getExecutionContext()),
                AuditStreamSupport.csv(e.getResult()),
                AuditStreamSupport.csv(e.getHttpMethod()),
                AuditStreamSupport.csv(e.getPath()),
                AuditStreamSupport.csv(e.getIp()),
                AuditStreamSupport.csv(e.getUserAgent()),
                AuditStreamSupport.csv(e.getEventFingerprint()),
                AuditStreamSupport.csv(e.getChainVersion()),
                AuditStreamSupport.csv(e.getPrevEventHash()),
                AuditStreamSupport.csv(e.getEventHash())
        ));
    }

    private static AuditPartition resolvePartition(RbacDeniedAuditEvent e) {
        if (e.getSubjectId() != null && !e.getSubjectId().isBlank()) {
            return AuditPartition.subject(STREAM, e.getSubjectId().trim());
        }
        return AuditPartition.global(STREAM);
    }

    private void recordSensitiveAccess(String action) {

        User actor = userService.getRequiredCurrentUser();
        UUID rootTenant = tenantService.getRootTenant().getId();
        String resourcePath = contextExtractor.fromCurrentRequest().resourcePath();

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenant,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                resourcePath,
                action,
                "RBAC denied audit operation",
                SensitiveDataClassification.REGULATED
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}