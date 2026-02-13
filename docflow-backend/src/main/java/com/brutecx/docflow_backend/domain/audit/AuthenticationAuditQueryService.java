package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditDTO;
import com.brutecx.docflow_backend.audit.AuditRequestContext;
import com.brutecx.docflow_backend.audit.AuditRequestContextExtractor;
import com.brutecx.docflow_backend.audit.EventFingerprint;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEvent;
import com.brutecx.docflow_backend.audit.auth.AuthenticationEventRepository;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.audit.sensitive.ISensitiveAccessAuditService;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessSubjectType;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveDataClassification;
import com.brutecx.docflow_backend.domain.tenant.TenantService;
import com.brutecx.docflow_backend.domain.user.User;
import com.brutecx.docflow_backend.domain.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationAuditQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 200_000;
    private static final int VERIFY_PAGE_SIZE = 2_000;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "timestamp",
            "username",
            "correlationId",
            "result"
    );

    private final AuthenticationEventRepository repository;
    private final ISensitiveAccessAuditService sensitiveAccessAuditService;
    private final AuditRequestContextExtractor auditRequestContextExtractor;
    private final UserService userService;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public Page<AuthenticationAuditDTO> query(
            Instant from,
            Instant to,
            String correlationId,
            int page,
            int size,
            String sortField,
            Sort.Direction direction,
            String username,
            AuthenticationResult result
    ) {

        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        Sort.Direction safeDirection = direction != null ? direction : Sort.Direction.DESC;
        String safeSortField = validateSortField(sortField);

        // Stable ordering: always add id as tie-breaker to avoid pagination anomalies
        Sort sort = Sort.by(safeDirection, safeSortField).and(Sort.by(safeDirection, "id"));

        Pageable pageable = PageRequest.of(page, safeSize, sort);

        Specification<AuthenticationEvent> spec = baseSpec(from, to, correlationId, username, result);

        Page<AuthenticationEvent> resultPage = repository.findAll(spec, pageable);

        recordSensitiveAccess();
        return resultPage.map(AuthenticationAuditDTO::from);
    }

    @Transactional(readOnly = true)
    public Page<AuthenticationAuditDTO> queryByCursor(
            Instant from,
            Instant to,
            String correlationId,
            String username,
            AuthenticationResult result,
            Cursor cursor,
            int limit,
            Sort.Direction direction
    ) {
        int safeLimit = Math.min(limit, MAX_PAGE_SIZE);
        Sort.Direction safeDirection = direction != null ? direction : Sort.Direction.DESC;

        // Cursor mode must be stable; we enforce timestamp + id ordering
        Sort sort = Sort.by(safeDirection, "timestamp").and(Sort.by(safeDirection, "id"));
        Pageable pageable = PageRequest.of(0, safeLimit, sort);

        Specification<AuthenticationEvent> spec = baseSpec(from, to, correlationId, username, result);
        if (cursor != null) {
            boolean asc = safeDirection.isAscending();
            spec = Specification.allOf(spec, AuthenticationAuditSpecifications.afterCursor(cursor.timestamp(), cursor.id(), asc));
        }

        Page<AuthenticationEvent> page = repository.findAll(spec, pageable);
        recordSensitiveAccess();
        return page.map(AuthenticationAuditDTO::from);
    }

    @Transactional(readOnly = true)
    public ExportSlice exportEvidence(
            Instant from,
            Instant to,
            String correlationId,
            String username,
            AuthenticationResult result
    ) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Export requires both 'from' and 'to' parameters");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must be >= 'from'");
        }

        Specification<AuthenticationEvent> spec = baseSpec(from, to, correlationId, username, result);
        long count = repository.count(spec);
        if (count > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("Export exceeds maximum row limit: " + MAX_EXPORT_ROWS);
        }

        // Evidence export should be replay-friendly: oldest -> newest, stable tie-breaker
        Sort sort = Sort.by(Sort.Direction.ASC, "timestamp").and(Sort.by(Sort.Direction.ASC, "id"));

        recordSensitiveAccess();
        return new ExportSlice(spec, sort, count);
    }

    @Transactional(readOnly = true)
    public VerificationReport verifyContinuity(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Verification requires both 'from' and 'to' parameters");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must be >= 'from'");
        }

        Specification<AuthenticationEvent> windowSpec =
                Specification.allOf(
                        AuthenticationAuditSpecifications.timestampFrom(from),
                        AuthenticationAuditSpecifications.timestampTo(to)
                );

        Sort sortAsc = Sort.by(Sort.Direction.ASC, "timestamp").and(Sort.by(Sort.Direction.ASC, "id"));

        // Fetch first event in window to do a boundary check.
        AuthenticationEvent first = repository.findAll(windowSpec, PageRequest.of(0, 1, sortAsc))
                .stream().findFirst().orElse(null);
        if (first == null) {
            recordSensitiveAccess();
            return VerificationReport.empty(from, to);
        }

        // Best boundary check: find the immediately previous event (by (timestamp,id) ordering).
        Specification<AuthenticationEvent> prevSpec =
                (root, query, cb) -> {

                    var ts = root.get("timestamp").as(Instant.class);
                    var id = root.get("id").as(UUID.class);

                    return cb.or(
                            cb.lessThan(ts, first.getTimestamp()),
                            cb.and(
                                    cb.equal(ts, first.getTimestamp()),
                                    cb.lessThan(id, first.getId())
                            )
                    );
                };

        AuthenticationEvent prev = repository.findAll(prevSpec, PageRequest.of(0, 1,
                        Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id"))))
                .stream().findFirst().orElse(null);

        int checked = 0;
        int mismatches = 0;
        VerificationMismatch firstMismatch = null;

        String expectedPrevHash = (prev != null ? prev.getEventHash() : "-");
        if (!Objects.equals(first.getPrevEventHash(), expectedPrevHash)) {
            mismatches++;
            firstMismatch = new VerificationMismatch(
                    first.getId(),
                    first.getTimestamp(),
                    expectedPrevHash,
                    first.getPrevEventHash()
            );
        }

        // Stream through window in stable order, checking linkage.
        Cursor cursor = new Cursor(first.getTimestamp(), first.getId());
        String lastEventHash = first.getEventHash();
        checked++; // first accounted for

        while (true) {
            Page<AuthenticationEvent> page = repository.findAll(
                    Specification.allOf(windowSpec,
                            AuthenticationAuditSpecifications.afterCursor(cursor.timestamp(), cursor.id(), true)),
                    PageRequest.of(0, VERIFY_PAGE_SIZE, sortAsc)
            );
            if (page.isEmpty()) {
                break;
            }

            for (AuthenticationEvent e : page.getContent()) {
                checked++;
                if (!Objects.equals(e.getPrevEventHash(), lastEventHash)) {
                    mismatches++;
                    if (firstMismatch == null) {
                        firstMismatch = new VerificationMismatch(
                                e.getId(),
                                e.getTimestamp(),
                                lastEventHash,
                                e.getPrevEventHash()
                        );
                    }
                }
                lastEventHash = e.getEventHash();
                cursor = new Cursor(e.getTimestamp(), e.getId());
            }
        }

        recordSensitiveAccess();

        return new VerificationReport(
                from,
                to,
                checked,
                mismatches,
                first.getId(),
                first.getTimestamp(),
                cursor.id(),
                cursor.timestamp(),
                firstMismatch
        );
    }

    public record Cursor(Instant timestamp, UUID id) {
    }

    public record ExportSlice(
            Specification<AuthenticationEvent> spec,
            Sort sort,
            long totalRows
    ) {
    }

    public record VerificationMismatch(
            UUID eventId,
            Instant timestamp,
            String expectedPrevHash,
            String actualPrevHash
    ) {
    }

    public record VerificationReport(
            Instant from,
            Instant to,
            int checked,
            int mismatches,
            UUID firstEventId,
            Instant firstEventTimestamp,
            UUID lastEventId,
            Instant lastEventTimestamp,
            VerificationMismatch firstMismatch
    ) {
        static VerificationReport empty(Instant from, Instant to) {
            return new VerificationReport(from, to, 0, 0, null, null, null, null, null);
        }
    }

    private Specification<AuthenticationEvent> baseSpec(
            Instant from,
            Instant to,
            String correlationId,
            String username,
            AuthenticationResult result
    ) {
        return Specification.allOf(
                from != null ? AuthenticationAuditSpecifications.timestampFrom(from) : null,
                to != null ? AuthenticationAuditSpecifications.timestampTo(to) : null,
                correlationId != null && !correlationId.isBlank()
                        ? AuthenticationAuditSpecifications.hasCorrelationId(correlationId)
                        : null,
                username != null && !username.isBlank()
                        ? AuthenticationAuditSpecifications.hasUsername(username)
                        : null,
                result != null
                        ? AuthenticationAuditSpecifications.hasResult(result)
                        : null
        );
    }

    private void recordSensitiveAccess() {
        User actor = userService.getRequiredCurrentUser();
        AuditRequestContext ctx =
                auditRequestContextExtractor.fromCurrentRequest();

        UUID tenantId = tenantService.getRootTenant().getId();

        String fingerprint = EventFingerprint.of(List.of(
                "SENSITIVE_ACCESS",
                "AUDIT_READ",
                "AUTHENTICATION",
                actor.getId().toString(),
                tenantId.toString(),
                ctx.correlationId()
        ));

        sensitiveAccessAuditService.record(
                actor.getId(),
                actor.getExternalSubjectId(),
                tenantId,
                SensitiveAccessSubjectType.AUDIT_STREAM,
                "AUTHENTICATION",
                "AUDIT",
                "READ",
                null,
                ctx.correlationId(),
                ctx.ip(),
                ctx.userAgent(),
                "AUDIT_READ",
                "Read authentication audit stream",
                SensitiveDataClassification.REGULATED,
                fingerprint
        );
    }

    private String validateSortField(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return "timestamp";
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            throw new IllegalArgumentException("Unsupported sort field");
        }
        return sortField;
    }

    @Transactional(readOnly = true)
    public void streamExportCsv(
            OutputStream outputStream,
            Instant from,
            Instant to,
            String correlationId,
            String username,
            AuthenticationResult result
    ) {
        ExportSlice slice = exportEvidence(from, to, correlationId, username, result);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            w.println(String.join(",",
                    "timestamp",
                    "source",
                    "username",
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
                    "eventHash",
                    "id"
            ));

            Sort sort = slice.sort();
            Pageable pageable = PageRequest.of(0, 2000, sort);

            while (true) {
                Page<AuthenticationEvent> page = repository.findAll(slice.spec(), pageable);
                if (page.isEmpty()) break;
                for (AuthenticationEvent e : page.getContent()) {
                    w.println(String.join(",",
                            csv(e.getTimestamp()),
                            csv(e.getSource()),
                            csv(e.getUsername()),
                            csv(e.getResult()),
                            csv(e.getIdp()),
                            csv(e.getIp()),
                            csv(e.getUserAgent()),
                            csv(e.getCorrelationId()),
                            csv(e.getCorrelationSource()),
                            csv(e.getExecutionContext()),
                            csv(e.getAuditResult()),
                            csv(e.getEventFingerprint()),
                            csv(Integer.toString(e.getChainVersion())),
                            csv(e.getPrevEventHash()),
                            csv(e.getEventHash()),
                            csv(e.getId().toString())
                    ));
                }
                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }
            w.flush();
        }
    }

    @Transactional(readOnly = true)
    public void streamExportJsonl(
            OutputStream outputStream,
            ObjectMapper objectMapper,
            Instant from,
            Instant to,
            String correlationId,
            String username,
            AuthenticationResult result
    ) {
        ExportSlice slice = exportEvidence(from, to, correlationId, username, result);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            Sort sort = slice.sort();
            Pageable pageable = PageRequest.of(0, 2000, sort);

            while (true) {
                Page<AuthenticationEvent> page = repository.findAll(slice.spec(), pageable);
                if (page.isEmpty()) break;
                for (AuthenticationEvent e : page.getContent()) {
                    AuthenticationAuditDTO dto = AuthenticationAuditDTO.from(e);
                    try {
                        w.println(objectMapper.writeValueAsString(dto));
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to serialize export record", ex);
                    }
                }
                if (!page.hasNext()) break;
                pageable = page.nextPageable();
            }
            w.flush();
        }
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
