package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditCursorPageDTO;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.domain.audit.CredentialLifecycleAuditQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/credential-lifecycle")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class CredentialLifecycleAuditController {

    private final CredentialLifecycleAuditQueryService queryService;

    @GetMapping
    public ResponseEntity<CredentialLifecycleAuditCursorPageDTO> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String subjectExternalId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorTimestamp,
            @RequestParam(required = false) UUID cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                queryService.query(
                        from, to, correlationId, subjectExternalId,
                        parseAuditResult(result),
                        cursorTimestamp, cursorId, size
                )
        );
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditVerificationResultDTO> verify(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(queryService.verify(from, to));
    }

    @GetMapping(value = "/export", produces = "application/x-ndjson")
    public void exportJsonl(
            HttpServletResponse response,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        response.setContentType("application/x-ndjson");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"credential-lifecycle-audit-export.jsonl\"");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        queryService.streamForensicExportJsonl(response, from, to);
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public void exportCsv(
            HttpServletResponse response,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"credential-lifecycle-audit-export.csv\"");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        queryService.streamForensicExportCsv(response, from, to);
    }

    private static AuditResult parseAuditResult(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AuditResult.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid result");
        }
    }
}
