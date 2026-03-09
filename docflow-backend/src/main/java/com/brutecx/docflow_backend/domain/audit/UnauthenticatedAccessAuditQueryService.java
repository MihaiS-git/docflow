package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.UnauthenticatedAccessAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.UnauthenticatedAccessAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.UnauthenticatedAccessAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessAuditEvent;
import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessAuditEventRepository;
import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessCanonicalMaterialBuilder;
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
public class UnauthenticatedAccessAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            UnauthenticatedAccessCanonicalMaterialBuilder.STREAM;

    private final UnauthenticatedAccessAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final UnauthenticatedAccessCanonicalMaterialBuilder canonicalBuilder;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditRequestContextExtractor contextExtractor;

    @Transactional(readOnly = true)
    public UnauthenticatedAccessAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {
        CursorQueryResult<UnauthenticatedAccessAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? UnauthenticatedAccessAuditSpecifications.timestampFrom(from) : null,
                                to != null ? UnauthenticatedAccessAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId)
                                        ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                                        : null,
                                cursorTimestamp != null
                                        ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                UnauthenticatedAccessAuditDTO::from,
                UnauthenticatedAccessAuditEvent::getTimestamp,
                UnauthenticatedAccessAuditEvent::getId
        );

        recordMeta("AUDIT_READ");

        return new UnauthenticatedAccessAuditCursorPageDTO(
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
                                UnauthenticatedAccessAuditSpecifications.timestampFrom(effectiveFrom),
                                UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                event -> AuditPartition.global(STREAM),
                event -> canonicalBuilder.buildCanonicalMaterial(
                        canonicalBuilder.fromEvent(event)
                ),
                UnauthenticatedAccessAuditEvent::getId,
                UnauthenticatedAccessAuditEvent::getTimestamp,
                UnauthenticatedAccessAuditEvent::getChainVersion,
                UnauthenticatedAccessAuditEvent::getPrevEventHash,
                UnauthenticatedAccessAuditEvent::getEventHash,
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
            String correlationId
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
                                UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                                UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                                hasText(correlationId)
                                        ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                                        : null,
                                cursorTs != null
                                        ? UnauthenticatedAccessAuditSpecifications.cursorAfter(cursorTs, cursorUuid, true)
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
                UnauthenticatedAccessAuditForensicExportDTO::from,
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to,
            String correlationId
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
                                UnauthenticatedAccessAuditSpecifications.timestampFrom(from),
                                UnauthenticatedAccessAuditSpecifications.timestampTo(to),
                                hasText(correlationId)
                                        ? UnauthenticatedAccessAuditSpecifications.hasCorrelationId(correlationId)
                                        : null
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
                "httpMethod",
                "path",
                "ip",
                "userAgent",
                "correlationId",
                "chainVersion",
                "prevEventHash",
                "eventHash"
        ));
    }

    private void writeCsvLine(PrintWriter w, UnauthenticatedAccessAuditEvent e) {
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
    }

    private void recordMeta(String action) {
        User actor = userService.getRequiredCurrentUser();
        UUID rootTenantId = tenantService.getRootTenant().getId();
        String resourcePath = contextExtractor.fromCurrentRequest().resourcePath();

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                resourcePath,
                action,
                "Unauthenticated access audit operation",
                SensitiveDataClassification.REGULATED
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}