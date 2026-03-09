package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEventRepository;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
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
public class CredentialLifecycleAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            CredentialLifecycleCanonicalMaterialBuilder.STREAM;

    private static final String PARTITION_ANON = "ANON";

    private final CredentialLifecycleAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final UserService userService;
    private final TenantService tenantService;
    private final CredentialLifecycleCanonicalMaterialBuilder canonicalBuilder;
    private final AuditChainService auditChainService;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditChainCheckpointRepository checkpointRepository;
    private final AuditRequestContextExtractor contextExtractor;

    @Transactional(readOnly = true)
    public CredentialLifecycleAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String subjectExternalId,
            AuditResult result,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {
        CursorQueryResult<CredentialLifecycleAuditDTO> page = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                true,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? CredentialLifecycleAuditSpecifications.timestampFrom(from) : null,
                                to != null ? CredentialLifecycleAuditSpecifications.timestampTo(to) : null,
                                hasText(correlationId) ? CredentialLifecycleAuditSpecifications.hasCorrelationId(correlationId) : null,
                                hasText(subjectExternalId) ? CredentialLifecycleAuditSpecifications.hasSubjectExternalId(subjectExternalId) : null,
                                result != null ? CredentialLifecycleAuditSpecifications.hasResult(result) : null,
                                cursorTimestamp != null
                                        ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                CredentialLifecycleAuditDTO::from,
                CredentialLifecycleAuditEvent::getTimestamp,
                CredentialLifecycleAuditEvent::getId
        );

        recordMeta("AUDIT_READ");

        return new CredentialLifecycleAuditCursorPageDTO(
                page.items(),
                page.hasMore(),
                page.nextCursorTimestamp(),
                page.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {
        AuditStreamSupport.validateRangeRequired(from, to);
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
                                CredentialLifecycleAuditSpecifications.timestampFrom(effectiveFrom),
                                CredentialLifecycleAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                this::resolvePartition,
                e -> canonicalBuilder.buildCanonicalMaterial(
                        canonicalBuilder.fromEvent(e)
                ),
                CredentialLifecycleAuditEvent::getId,
                CredentialLifecycleAuditEvent::getTimestamp,
                CredentialLifecycleAuditEvent::getChainVersion,
                CredentialLifecycleAuditEvent::getPrevEventHash,
                CredentialLifecycleAuditEvent::getEventHash,
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
                                CredentialLifecycleAuditSpecifications.timestampFrom(from),
                                CredentialLifecycleAuditSpecifications.timestampTo(to),
                                cursorTs != null
                                        ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
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
                CredentialLifecycleAuditForensicExportDTO::from,
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
                                CredentialLifecycleAuditSpecifications.timestampFrom(from),
                                CredentialLifecycleAuditSpecifications.timestampTo(to)
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
                "subjectExternalId",
                "clientId",
                "sessionId",
                "ip",
                "eventType",
                "requiredAction",
                "correlationId",
                "correlationSource",
                "executionContext",
                "result",
                "reasonCode",
                "reasonDetail",
                "eventFingerprint",
                "chainVersion",
                "prevEventHash",
                "eventHash"
        ));
    }

    private void writeCsvLine(PrintWriter w, CredentialLifecycleAuditEvent e) {
        w.println(String.join(",",
                AuditStreamSupport.csv(e.getId()),
                AuditStreamSupport.csv(e.getTimestamp()),
                AuditStreamSupport.csv(e.getSubjectExternalId()),
                AuditStreamSupport.csv(e.getClientId()),
                AuditStreamSupport.csv(e.getSessionId()),
                AuditStreamSupport.csv(e.getIp()),
                AuditStreamSupport.csv(e.getEventType()),
                AuditStreamSupport.csv(e.getRequiredAction()),
                AuditStreamSupport.csv(e.getCorrelationId()),
                AuditStreamSupport.csv(e.getCorrelationSource()),
                AuditStreamSupport.csv(e.getExecutionContext()),
                AuditStreamSupport.csv(e.getResult()),
                AuditStreamSupport.csv(e.getReasonCode()),
                AuditStreamSupport.csv(e.getReasonDetail()),
                AuditStreamSupport.csv(e.getEventFingerprint()),
                AuditStreamSupport.csv(e.getChainVersion()),
                AuditStreamSupport.csv(e.getPrevEventHash()),
                AuditStreamSupport.csv(e.getEventHash())
        ));
    }

    private void recordMeta(String action) {
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
                "Credential lifecycle audit operation",
                SensitiveDataClassification.REGULATED
        );
    }

    private AuditPartition resolvePartition(CredentialLifecycleAuditEvent e) {
        String subjectValue =
                hasText(e.getSubjectExternalId())
                        ? e.getSubjectExternalId().trim()
                        : PARTITION_ANON;

        return AuditPartition.subject(STREAM, subjectValue);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}