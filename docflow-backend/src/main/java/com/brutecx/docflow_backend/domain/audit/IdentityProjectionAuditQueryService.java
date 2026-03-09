package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.identity.IdentityProjectionAuditEvent;
import com.brutecx.docflow_backend.audit.identity.IdentityProjectionAuditEventRepository;
import com.brutecx.docflow_backend.audit.identity.IdentityProjectionCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.tamper.AuditChainCheckpoint;
import com.brutecx.docflow_backend.audit.tamper.AuditChainCheckpointRepository;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.domain.audit.export.SealedJsonlAuditExportService;
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
public class IdentityProjectionAuditQueryService extends AbstractAuditStreamQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            IdentityProjectionCanonicalMaterialBuilder.STREAM;

    private final IdentityProjectionAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final IdentityProjectionCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;
    private final AuditChainCheckpointRepository checkpointRepository;

    @Transactional(readOnly = true)
    public IdentityProjectionAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String subjectId,
            String correlationId,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {
        CursorQueryResult<IdentityProjectionAuditDTO> result = executeCursorQuery(
                from,
                to,
                cursorTimestamp,
                cursorId,
                size,
                MAX_PAGE_SIZE,
                false,
                pageable -> repository.findAll(
                        Specification.allOf(
                                from != null ? IdentityProjectionAuditSpecifications.timestampFrom(from) : null,
                                to != null ? IdentityProjectionAuditSpecifications.timestampTo(to) : null,
                                hasText(subjectId) ? IdentityProjectionAuditSpecifications.hasSubjectId(subjectId) : null,
                                hasText(correlationId) ? IdentityProjectionAuditSpecifications.hasCorrelationId(correlationId) : null,
                                cursorTimestamp != null
                                        ? IdentityProjectionAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                                        : null
                        ),
                        pageable
                ),
                IdentityProjectionAuditDTO::from,
                IdentityProjectionAuditEvent::getTimestamp,
                IdentityProjectionAuditEvent::getId
        );

        return new IdentityProjectionAuditCursorPageDTO(
                result.items(),
                result.hasMore(),
                result.nextCursorTimestamp(),
                result.nextCursorId()
        );
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {
        Optional<AuditChainCheckpoint> checkpoint = checkpointRepository.findByStream(STREAM);
        Instant checkpointStart = AuditStreamSupport.resolveCheckpointStart(checkpoint);

        return executeVerification(
                from,
                to,
                checkpointStart,
                (effectiveFrom, cursorTimestamp, cursorId, pageable) -> repository.findAll(
                        Specification.allOf(
                                IdentityProjectionAuditSpecifications.timestampFrom(effectiveFrom),
                                IdentityProjectionAuditSpecifications.timestampTo(to),
                                cursorTimestamp != null
                                        ? IdentityProjectionAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                                        : null
                        ),
                        pageable
                ),
                this::resolvePartition,
                event -> canonicalMaterialBuilder.buildCanonicalMaterial(
                        canonicalMaterialBuilder.fromEvent(event)
                ),
                IdentityProjectionAuditEvent::getId,
                IdentityProjectionAuditEvent::getTimestamp,
                IdentityProjectionAuditEvent::getChainVersion,
                IdentityProjectionAuditEvent::getPrevEventHash,
                IdentityProjectionAuditEvent::getEventHash,
                auditChainService
        );
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
                                IdentityProjectionAuditSpecifications.timestampFrom(from),
                                IdentityProjectionAuditSpecifications.timestampTo(to),
                                cursorTs != null
                                        ? IdentityProjectionAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
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
                IdentityProjectionAuditForensicExportDTO::from,
                () -> {}
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
                                IdentityProjectionAuditSpecifications.timestampFrom(from),
                                IdentityProjectionAuditSpecifications.timestampTo(to),
                                AuditStreamSupport.notArchived()
                        ),
                        pageable
                ),
                this::writeCsvHeader,
                this::writeCsvLine,
                () -> {}
        );
    }

    private void writeCsvHeader(PrintWriter w) {
        w.println(String.join(",",
                "id",
                "timestamp",
                "subjectId",
                "correlationId",
                "executionContext",
                "correlationSource",
                "result",
                "reasonCode",
                "eventFingerprint",
                "chainVersion",
                "prevEventHash",
                "eventHash"
        ));
    }

    private void writeCsvLine(PrintWriter w, IdentityProjectionAuditEvent e) {
        w.println(String.join(",",
                AuditStreamSupport.csv(e.getId()),
                AuditStreamSupport.csv(e.getTimestamp()),
                AuditStreamSupport.csv(e.getSubjectId()),
                AuditStreamSupport.csv(e.getCorrelationId()),
                AuditStreamSupport.csv(e.getExecutionContext()),
                AuditStreamSupport.csv(e.getCorrelationSource()),
                AuditStreamSupport.csv(e.getResult()),
                AuditStreamSupport.csv(e.getReasonCode()),
                AuditStreamSupport.csv(e.getEventFingerprint()),
                AuditStreamSupport.csv(e.getChainVersion()),
                AuditStreamSupport.csv(e.getPrevEventHash()),
                AuditStreamSupport.csv(e.getEventHash())
        ));
    }

    private AuditPartition resolvePartition(IdentityProjectionAuditEvent e) {
        return AuditPartition.subject(STREAM, e.getSubjectId());
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}