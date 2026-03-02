package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditForensicExportDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEventRepository;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
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
public class CredentialLifecycleAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            CredentialLifecycleCanonicalMaterialBuilder.STREAM;

    private static final String PARTITION_ANON = "ANON";

    private final CredentialLifecycleAuditEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final CredentialLifecycleCanonicalMaterialBuilder canonicalBuilder;
    private final AuditChainService auditChainService;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

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
        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                from != null ? CredentialLifecycleAuditSpecifications.timestampFrom(from) : null,
                to != null ? CredentialLifecycleAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? CredentialLifecycleAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(subjectExternalId) ? CredentialLifecycleAuditSpecifications.hasSubjectExternalId(subjectExternalId) : null,
                result != null ? CredentialLifecycleAuditSpecifications.hasResult(result) : null,
                cursorTimestamp != null
                        ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<CredentialLifecycleAuditEvent> page = repository.findAll(spec, pageable);

        List<CredentialLifecycleAuditEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<CredentialLifecycleAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(CredentialLifecycleAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            CredentialLifecycleAuditEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordMeta("AUDIT_READ");

        return new CredentialLifecycleAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {
        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                    CredentialLifecycleAuditSpecifications.timestampFrom(from),
                    CredentialLifecycleAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<CredentialLifecycleAuditEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) break;

            for (CredentialLifecycleAuditEvent event : batch.getContent()) {

                AuditPartition partition = resolvePartition(event);

                String canonical = canonicalBuilder.buildCanonicalMaterial(
                        canonicalBuilder.fromEvent(event)
                );

                AuditVerificationResultDTO failure =
                        AuditStreamSupport.verifyEvent(
                                event.getId(),
                                partition,
                                event.getChainVersion(),
                                event.getPrevEventHash(),
                                event.getEventHash(),
                                canonical,
                                auditChainService,
                                lastHashByPartitionStateKey,
                                verified
                        );

                if (failure != null) {
                    recordMeta("AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTimestamp = event.getTimestamp();
                cursorId = event.getId();
            }
        }

        recordMeta("AUDIT_VERIFY");
        return AuditVerificationResultDTO.success(verified);
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

                    Specification<CredentialLifecycleAuditEvent> spec = Specification.allOf(
                            CredentialLifecycleAuditSpecifications.timestampFrom(from),
                            CredentialLifecycleAuditSpecifications.timestampTo(to),
                            cursorTs != null
                                    ? CredentialLifecycleAuditSpecifications.cursorAfter(cursorTs, cursorId, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
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
                                CredentialLifecycleAuditSpecifications.timestampFrom(from),
                                CredentialLifecycleAuditSpecifications.timestampTo(to)
                        ),
                        pageable
                ),
                (PrintWriter w) -> w.println(String.join(",",
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
                )),
                (PrintWriter w, CredentialLifecycleAuditEvent e) -> {
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
                },
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    private void recordMeta(String action) {
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenant = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
                "SCOPE_GLOBAL",
                actor.getId().toString(),
                rootTenant.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenant,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                action,
                "Credential lifecycle audit operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private AuditPartition resolvePartition(CredentialLifecycleAuditEvent e) {
        String subjectValue = hasText(e.getSubjectExternalId())
                ? e.getSubjectExternalId().trim()
                : PARTITION_ANON;

        return AuditPartition.subject(STREAM, subjectValue);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}