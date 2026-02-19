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
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final int VERIFY_BATCH_SIZE = 2_000;
    private static final int EXPORT_BATCH_SIZE = 2_000;
    private static final int EXPORT_MAX_ROWS = 200_000;

    private static final String STREAM = AuthenticationAuditCanonicalMaterialBuilder.STREAM;
    private static final String PARTITION_ANON = "ANON";

    private final AuthenticationEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor ctxExtractor;
    private final UserService userService;
    private final TenantService tenantService;
    private final AuditChainService auditChainService;
    private final AuthenticationAuditCanonicalMaterialBuilder canonicalMaterialBuilder;
    private final ObjectMapper objectMapper;

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

        GoldAuditSupport.validateRange(from, to);
        GoldAuditSupport.validateCursorPair(cursorTimestamp, cursorId);

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
       VERIFY – timestamp ASC, id ASC
       ===================================================== */

    @Transactional(readOnly = true)
    public AuditVerificationResultDTO verify(Instant from, Instant to) {

        GoldAuditSupport.validateRangeRequired(from, to);

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

                AuthenticationAuditCanonicalMaterialBuilder.Input input =
                        canonicalMaterialBuilder.fromEvent(e);

                String canonicalMaterial =
                        canonicalMaterialBuilder.buildCanonicalMaterial(input);

                AuditVerificationResultDTO failure =
                        GoldAuditSupport.verifyEvent(
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
       EXPORT JSONL + CSV (ASC timestamp, ASC id)
       ===================================================== */

    @Transactional(readOnly = true)
    public void streamForensicExportJsonl(
            HttpServletResponse response,
            Instant from,
            Instant to
    ) {
        streamExport(response, from, to, false);
    }

    @Transactional(readOnly = true)
    public void streamForensicExportCsv(
            HttpServletResponse response,
            Instant from,
            Instant to
    ) {
        streamExport(response, from, to, true);
    }

    private void streamExport(
            HttpServletResponse response,
            Instant from,
            Instant to,
            boolean csv
    ) {

        GoldAuditSupport.validateRangeRequired(from, to);

        GoldAuditSupport.streamExportAsc(
                response,
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
                    if (!csv) return;
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
                            "auditResult",
                            "eventFingerprint",
                            "chainVersion",
                            "prevEventHash",
                            "eventHash"
                    ));
                },
                (PrintWriter w, AuthenticationEvent e) -> {
                    if (csv) {
                        writeCsvLine(w, e);
                    } else {
                        w.println(objectMapper.writeValueAsString(
                                AuthenticationAuditForensicExportDTO.from(e)
                        ));
                    }
                },
                () -> recordMeta("AUDIT_EXPORT")
        );
    }

    private void writeCsvLine(PrintWriter w, AuthenticationEvent e) {
        w.println(String.join(",",
                GoldAuditSupport.csv(e.getId()),
                GoldAuditSupport.csv(e.getTimestamp()),
                GoldAuditSupport.csv(e.getSource()),
                GoldAuditSupport.csv(e.getUsername()),
                GoldAuditSupport.csv(e.getSubjectId()),
                GoldAuditSupport.csv(e.getResult()),
                GoldAuditSupport.csv(e.getIdp()),
                GoldAuditSupport.csv(e.getIp()),
                GoldAuditSupport.csv(e.getUserAgent()),
                GoldAuditSupport.csv(e.getCorrelationId()),
                GoldAuditSupport.csv(e.getCorrelationSource()),
                GoldAuditSupport.csv(e.getExecutionContext()),
                GoldAuditSupport.csv(e.getResult()),
                GoldAuditSupport.csv(e.getEventFingerprint()),
                GoldAuditSupport.csv(e.getChainVersion()),
                GoldAuditSupport.csv(e.getPrevEventHash()),
                GoldAuditSupport.csv(e.getEventHash())
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
                "Audit stream operation",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    /* =====================================================
       PARTITION RESOLUTION
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

    /* =====================================================
       UTIL
       ===================================================== */

    private AuthenticationResult parseResult(String raw) {
        if (!hasText(raw)) return null;
        return AuthenticationResult.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
