package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.auth.*;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            AuthenticationAuditCanonicalMaterialBuilder.STREAM;

    private static final String PARTITION_ANON = "ANON";

    private final AuthenticationEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditChainCheckpointRepository checkpointRepository;
    private final AuditRequestContextExtractor contextExtractor;

    @Transactional(readOnly = true)
    public AuthenticationAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String username,
            String subjectId,
            AuthenticationResult result,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {
        CursorQueryResult<AuthenticationAuditDTO> resultPage = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                true,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? AuthenticationAuditSpecifications.timestampFrom(from) : null,
                                to != null ? AuthenticationAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId) ? AuthenticationAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(username) ? AuthenticationAuditSpecifications.hasUsername(username) : null,
                                hasText(subjectId) ? AuthenticationAuditSpecifications.hasSubjectId(subjectId) : null,
                                result != null ? AuthenticationAuditSpecifications.hasResult(result) : null,
                                cursorTimestamp != null
                                        ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                AuthenticationAuditDTO::from,
                AuthenticationEvent::getTimestamp,
                AuthenticationEvent::getId
        );

        recordMeta("AUDIT_READ");

        return new AuthenticationAuditCursorPageDTO(
                resultPage.items(),
                resultPage.hasMore(),
                resultPage.nextCursorTimestamp(),
                resultPage.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {
        Optional<AuditChainCheckpoint> checkpoint =
                checkpointRepository.findByStream(STREAM);
        Instant checkpointStart =
                AuditStreamSupport.resolveCheckpointStart(checkpoint);
        AuditVerificationResultDTO result = executeVerification(
                from,
                to,
                checkpointStart,
                (effectiveFrom, cursorTimestamp, cursorId, pageable) -> repository.findAll(
                        Specification.allOf(
                                AuthenticationAuditSpecifications.timestampFrom(effectiveFrom),
                                AuthenticationAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                this::resolvePartition,
                e -> canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(e)
                ),
                AuthenticationEvent::getId,
                AuthenticationEvent::getTimestamp,
                AuthenticationEvent::getChainVersion,
                AuthenticationEvent::getPrevEventHash,
                AuthenticationEvent::getEventHash,
                auditChainService
        );
        recordMeta("AUDIT_VERIFY");

        return result;
    }

    @Transactional
    public void streamForensicExportJsonl(
            OutputStream out,
            Instant from,
            Instant to
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
                                AuthenticationAuditSpecifications.timestampFrom(from),
                                AuthenticationAuditSpecifications.timestampTo(to),
                                cursorTs != null
                                        ? AuthenticationAuditSpecifications.afterCursor(cursorTs, cursorId, true)
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
                AuthenticationAuditForensicExportDTO::from,
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to
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
                                AuthenticationAuditSpecifications.timestampFrom(from),
                                AuthenticationAuditSpecifications.timestampTo(to)
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
                "id","timestamp","source","username","subjectId","result",
                "idp","ip","userAgent","correlationId","correlationSource",
                "executionContext","eventFingerprint","chainVersion",
                "prevEventHash","eventHash"
        ));
    }

    private void writeCsvLine(PrintWriter w, AuthenticationEvent e) {
        w.println(String.join(",",
                AuditStreamSupport.csv(e.getId()),
                AuditStreamSupport.csv(e.getTimestamp()),
                AuditStreamSupport.csv(e.getSource()),
                AuditStreamSupport.csv(e.getUsername()),
                AuditStreamSupport.csv(e.getSubjectId()),
                AuditStreamSupport.csv(e.getResult()),
                AuditStreamSupport.csv(e.getIdp()),
                AuditStreamSupport.csv(e.getIp()),
                AuditStreamSupport.csv(e.getUserAgent()),
                AuditStreamSupport.csv(e.getCorrelationId()),
                AuditStreamSupport.csv(e.getCorrelationSource()),
                AuditStreamSupport.csv(e.getExecutionContext()),
                AuditStreamSupport.csv(e.getEventFingerprint()),
                AuditStreamSupport.csv(e.getChainVersion()),
                AuditStreamSupport.csv(e.getPrevEventHash()),
                AuditStreamSupport.csv(e.getEventHash())
        ));
    }

    private AuditPartition resolvePartition(AuthenticationEvent e) {
        if (hasText(e.getSubjectId())) {
            return AuditPartition.subject(STREAM, e.getSubjectId().trim());
        }

        if (hasText(e.getUsername())) {
            return AuditPartition.subject(
                    STREAM,
                    e.getUsername().trim().toLowerCase(Locale.ROOT)
            );
        }

        return AuditPartition.subject(STREAM, PARTITION_ANON);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
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
                "Authentication audit stream operation",
                SensitiveDataClassification.REGULATED
        );
    }
}