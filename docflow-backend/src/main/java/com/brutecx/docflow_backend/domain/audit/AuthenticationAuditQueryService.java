package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.*;
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

import java.io.PrintWriter;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthenticationAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int VERIFY_BATCH_SIZE = 1_000;
    private static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM =
            AuthenticationAuditCanonicalMaterialBuilder.STREAM;

    private static final String PARTITION_ANON = "ANON";

    private final AuthenticationEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final SealedJsonlAuditExportService sealedJsonlAuditExportService;

    /* =====================================================
       CURSOR QUERY – DESC timestamp, DESC id
       ===================================================== */

    @Transactional(readOnly = true)
    public AuthenticationAuditCursorPageDTO query(
            Instant from,
            Instant to,
            String correlationId,
            String username,
            String subjectId,
            String resultRaw,
            Instant cursorTimestamp,
            UUID cursorId,
            int size
    ) {

        AuditStreamSupport.validateRange(from, to);
        AuditStreamSupport.validateCursorPair(cursorTimestamp, cursorId);

        AuthenticationResult result = parseResult(resultRaw);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                0,
                safeSize + 1,
                Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"))
        );

        Specification<AuthenticationEvent> spec = Specification.allOf(
                from != null ? AuthenticationAuditSpecifications.timestampFrom(from) : null,
                to != null ? AuthenticationAuditSpecifications.timestampTo(to) : null,
                hasText(correlationId) ? AuthenticationAuditSpecifications.hasCorrelationId(correlationId) : null,
                hasText(username) ? AuthenticationAuditSpecifications.hasUsername(username) : null,
                hasText(subjectId) ? AuthenticationAuditSpecifications.hasSubjectId(subjectId) : null,
                result != null ? AuthenticationAuditSpecifications.hasResult(result) : null,
                cursorTimestamp != null
                        ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, false)
                        : null
        );

        Page<AuthenticationEvent> page = repository.findAll(spec, pageable);

        List<AuthenticationEvent> raw = page.getContent();
        boolean hasMore = raw.size() > safeSize;

        List<AuthenticationAuditDTO> items = new ArrayList<>(Math.min(raw.size(), safeSize));
        for (int i = 0; i < raw.size() && i < safeSize; i++) {
            items.add(AuthenticationAuditDTO.from(raw.get(i)));
        }

        Instant nextTs = null;
        UUID nextId = null;

        if (hasMore) {
            AuthenticationEvent last = raw.get(safeSize - 1);
            nextTs = last.getTimestamp();
            nextId = last.getId();
        }

        recordMeta("AUDIT_READ");

        return new AuthenticationAuditCursorPageDTO(items, hasMore, nextTs, nextId);
    }

    /* =====================================================
       VERIFY – ASC timestamp, ASC id
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        AuditStreamSupport.validateRangeRequired(from, to);

        Map<String, String> lastHashByPartitionStateKey = new HashMap<>();
        long verified = 0;

        Instant cursorTimestamp = null;
        UUID cursorId = null;

        while (true) {

            Specification<AuthenticationEvent> spec = Specification.allOf(
                    AuthenticationAuditSpecifications.timestampFrom(from),
                    AuthenticationAuditSpecifications.timestampTo(to),
                    cursorTimestamp != null
                            ? AuthenticationAuditSpecifications.afterCursor(cursorTimestamp, cursorId, true)
                            : null
            );

            Pageable pageable = PageRequest.of(
                    0,
                    VERIFY_BATCH_SIZE,
                    Sort.by(Sort.Order.asc("timestamp"), Sort.Order.asc("id"))
            );

            Page<AuthenticationEvent> batch = repository.findAll(spec, pageable);
            if (batch.isEmpty()) {
                recordMeta("AUDIT_VERIFY");
                return AuditVerificationResultDTO.success(verified);
            }

            for (AuthenticationEvent e : batch.getContent()) {

                AuditPartition partition = resolvePartition(e);

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
                    recordMeta("AUDIT_VERIFY");
                    return failure;
                }

                verified++;
                cursorTimestamp = e.getTimestamp();
                cursorId = e.getId();
            }
        }
    }

    /* =====================================================
       SEALED JSONL EXPORT
       ===================================================== */

    @Transactional
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to
    ) {

        AuditStreamSupport.validateRangeRequired(from, to);

        sealedJsonlAuditExportService.exportSealedJsonl(
                response,
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

                    Specification<AuthenticationEvent> spec = Specification.allOf(
                            AuthenticationAuditSpecifications.timestampFrom(from),
                            AuthenticationAuditSpecifications.timestampTo(to),
                            cursorTs != null
                                    ? AuthenticationAuditSpecifications.afterCursor(cursorTs, cursorId, true)
                                    : null
                    );

                    return repository.findAll(spec, pageable);
                },
                AuthenticationAuditForensicExportDTO::from,
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    /* =====================================================
       CSV EXPORT
       ===================================================== */

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
                pageable -> {
                    Specification<AuthenticationEvent> spec = Specification.allOf(
                            AuthenticationAuditSpecifications.timestampFrom(from),
                            AuthenticationAuditSpecifications.timestampTo(to)
                    );
                    return repository.findAll(spec, pageable);
                },
                (PrintWriter w) -> {
                    w.println(String.join(",",
                            "id",
                            "timestamp",
                            "source",
                            "username",
                            "subjectId",
                            "result",
                            "idp",
                            "ip",
                            "userAgent",
                            "correlationId",
                            "correlationSource",
                            "executionContext",
                            "eventFingerprint",
                            "chainVersion",
                            "prevEventHash",
                            "eventHash"
                    ));
                },
                (PrintWriter w, AuthenticationEvent e) -> writeCsvLine(w, e),
                () -> recordMeta("AUDIT_EXPORT")
        );
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

    /* =====================================================
       META AUDIT
       ===================================================== */

    private void recordMeta(String action) {

        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx = ctxExtractor.fromCurrentRequest();
        UUID rootTenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                action,
                STREAM,
                "SCOPE_GLOBAL",
                actor.getId().toString(),
                rootTenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                rootTenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                STREAM,
                "AUDIT",
                action,
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                action,
                "Authentication audit stream operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    /* =====================================================
       PARTITION
       ===================================================== */

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

    private AuthenticationResult parseResult(String raw) {
        if (!hasText(raw)) return null;
        return AuthenticationResult.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}