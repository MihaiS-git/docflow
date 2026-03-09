package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEventRepository;
import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedCanonicalMaterialBuilder;
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
public class LifecycleDeniedAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            LifecycleDeniedCanonicalMaterialBuilder.STREAM;

    private final LifecycleDeniedAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final LifecycleDeniedCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditRequestContextExtractor contextExtractor;

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
        CursorQueryResult<LifecycleDeniedAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? LifecycleDeniedAuditSpecifications.timestampFrom(from) : null,
                                to != null ? LifecycleDeniedAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                                cursorTimestamp != null
                                        ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                LifecycleDeniedAuditDTO::from,
                LifecycleDeniedAuditEvent::getTimestamp,
                LifecycleDeniedAuditEvent::getId
        );

        recordSensitiveAccess("AUDIT_READ");

        return new LifecycleDeniedAuditCursorPageDTO(
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
                                LifecycleDeniedAuditSpecifications.timestampFrom(effectiveFrom),
                                LifecycleDeniedAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                this::resolvePartition,
                event -> canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                ),
                LifecycleDeniedAuditEvent::getId,
                LifecycleDeniedAuditEvent::getTimestamp,
                LifecycleDeniedAuditEvent::getChainVersion,
                LifecycleDeniedAuditEvent::getPrevEventHash,
                LifecycleDeniedAuditEvent::getEventHash,
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
                (cursorTs, cursorId) -> repository.findAll(
                        Specification.allOf(
                                LifecycleDeniedAuditSpecifications.timestampFrom(from),
                                LifecycleDeniedAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null,
                                cursorTs != null
                                        ? LifecycleDeniedAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
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
                LifecycleDeniedAuditForensicExportDTO::from,
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
                                LifecycleDeniedAuditSpecifications.timestampFrom(from),
                                LifecycleDeniedAuditSpecifications.timestampTo(to),
                                hasText(correlationId) ? LifecycleDeniedAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectId) ? LifecycleDeniedAuditSpecifications.hasSubjectId(subjectId) : null
                        ),
                        pageable
                ),
                this::writeCsvHeader,
                this::writeCsvLine,
                () -> recordSensitiveAccess("AUDIT_EXPORT")
        );
    }

    private void writeCsvHeader(PrintWriter w) {
        w.println(String.join(",",
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
        ));
    }

    private void writeCsvLine(PrintWriter w, LifecycleDeniedAuditEvent e) {
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
    }

    private AuditPartition resolvePartition(LifecycleDeniedAuditEvent e) {
        if (hasText(e.getSubjectId())) {
            return AuditPartition.subject(STREAM, e.getSubjectId().trim());
        }
        return AuditPartition.global(STREAM);
    }

    private void recordSensitiveAccess(String action) {
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
                "Lifecycle denied audit operation",
                SensitiveDataClassification.REGULATED
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}