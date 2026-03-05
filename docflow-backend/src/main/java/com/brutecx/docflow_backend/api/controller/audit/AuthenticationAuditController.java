package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.domain.audit.AuthenticationAuditQueryService;
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
@RequestMapping("/api/audit/authentication")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('AUDITOR')")
public class AuthenticationAuditController {

    private final AuthenticationAuditQueryService queryService;

    @GetMapping
    public ResponseEntity<AuthenticationAuditCursorPageDTO> query(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            String username,

            @RequestParam(required = false)
            String subjectId,

            @RequestParam(required = false)
            String result,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant cursorTimestamp,

            @RequestParam(required = false)
            UUID cursorId,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        AuthenticationResult parsedResult = null;

        if (result != null && !result.isBlank()) {
            try {
                parsedResult = AuthenticationResult.valueOf(result.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // ignore
            }
        }

        return ResponseEntity.ok(
                queryService.query(
                        from,
                        to,
                        correlationId,
                        username,
                        subjectId,
                        parsedResult,
                        cursorTimestamp,
                        cursorId,
                        size
                )
        );
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditVerificationResultDTO> verify(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(
                queryService.verify(from, to)
        );
    }

    @GetMapping(value = "/export", produces = "application/x-ndjson")
    public void exportJsonl(
            HttpServletResponse response,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) throws IOException {

        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"authentication-audit-export.jsonl\""
        );
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        queryService.streamForensicExportJsonl(
                response.getOutputStream(),
                from,
                to
        );
    }

    @GetMapping("/export/csv")
    public void exportCsv(
            HttpServletResponse response,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        queryService.streamForensicExportCsv(
                response,
                from,
                to
        );
    }
}