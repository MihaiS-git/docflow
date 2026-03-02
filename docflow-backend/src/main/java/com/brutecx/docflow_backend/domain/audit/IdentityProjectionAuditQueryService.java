package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.identity.*;
import com.brutecx.docflow_backend.audit.tamper.AuditChainService;
import com.brutecx.docflow_backend.audit.tamper.AuditPartition;
import com.brutecx.docflow_backend.domain.audit.export.SealedJsonlAuditExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IdentityProjectionAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            IdentityProjectionCanonicalMaterialBuilder.STREAM;

    private final IdentityProjectionAuditEventRepository repository;
    private final AuditChainService auditChainService;
    private final IdentityProjectionCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

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
        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Specification<IdentityProjectionAuditEvent> spec = Specification.allOf(
                from != null ? IdentityProjectionAuditSpecifications.timestampFrom(from) : null,
                to != null ? IdentityProjectionAuditSpecifications.timestampTo(to) : null,
                hasText(subjectId) ? IdentityProjectionAuditSpecifications.hasSubjectId(subjectId) : null,
                hasText(correlationId) ? IdentityProjectionAuditSpecifications.hasCorrelationId(correlationId) : null,
                cursorTimestamp != null
                        ? IdentityProjectionAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<IdentityProjectionAuditEvent> page = repository.findAll(spec, pageable);

        List<IdentityProjectionAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<IdentityProjectionAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(IdentityProjectionAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            IdentityProjectionAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        return new IdentityProjectionAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {
        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTs = null;
        UUID cursorId = null;

        while (true) {

            Specification<IdentityProjectionAuditEvent> spec = Specification.allOf(
                    IdentityProjectionAuditSpecifications.timestampFrom(from),
                    IdentityProjectionAuditSpecifications.timestampTo(to),
                    cursorTs != null
                            ? IdentityProjectionAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<IdentityProjectionAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                return AuditVerificationResultDTO.success(verified);
            }

            for (IdentityProjectionAuditEvent e : batch.getContent()) {

                AuditPartition partition =
                        AuditPartition.subject(STREAM, e.getSubjectId());

                String canonicalMaterial =
                        canonicalMaterialBuilder.buildCanonicalMaterial(
                                canonicalMaterialBuilder.fromEvent(e)
                        );

                AuditVerificationResultDTO failure =
                        AuditStreamSupport.verifyEvent(
                                e.getId(),
                                partition,
                                e.getChainVersion(),
                                e.getPrevEventHash(),
                                e.getEventHash(),
                                canonicalMaterial,
                                auditChainService,
                                lastHashByPartitionStateKey,
                                verified
                        );

                if (failure != null) {
                    return failure;
                }

                verified++;
                cursorTs = e.getTimestamp();
                cursorId = e.getId();
            }
        }
    }

    @Transactional
    public void streamForensicExportJsonl(
            OutputStream out,
            Instant from,
            Instant to
    ) {
        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                out,
                STREAM,
                from,
                to,
                null,
                EXPORT_MAX_ROWS,
                (Instant cursorTs, UUID cursorId) -> {

                    Pageable pageable = PageRequest.of(
                            0,
                            EXPORT_BATCH_SIZE,
                            Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
                    );

                    Specification<IdentityProjectionAuditEvent> spec = Specification.allOf(
                            IdentityProjectionAuditSpecifications.timestampFrom(from),
                            IdentityProjectionAuditSpecifications.timestampTo(to),
                            cursorTs != null
                                    ? IdentityProjectionAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
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
        AuditStreamSupport.validateRangeRequired(from, to);

        AuditStreamSupport.streamExportCsvAsc(
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
                (PrintWriter w) -> w.println(String.join(",",
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
                )),
                (PrintWriter w, IdentityProjectionAuditEvent e) -> {
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
                },
                () -> {}
        );
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}