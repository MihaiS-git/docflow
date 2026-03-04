package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.IdentityProjectionAuditCursorPageDTO;
import com.brutecx.docflow_backend.domain.audit.IdentityProjectionAuditQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/identity-projection")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('AUDITOR')")
public class IdentityProjectionAuditController {

    private final IdentityProjectionAuditQueryService queryService;

    @GetMapping
    public ResponseEntity<IdentityProjectionAuditCursorPageDTO> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorTimestamp,
            @RequestParam(required = false) UUID cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                queryService.query(from, to, subjectId, correlationId, cursorTimestamp, cursorId, size)
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
    ) throws IOException {

        response.setContentType("application/x-ndjson");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"identity-projection-audit-export.jsonl\"");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        queryService.streamForensicExportJsonl(response.getOutputStream(), from, to);
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public void exportCsv(
            HttpServletResponse response,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"identity-projection-audit-export.csv\"");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        queryService.streamForensicExportCsv(response, from, to);
    }
}