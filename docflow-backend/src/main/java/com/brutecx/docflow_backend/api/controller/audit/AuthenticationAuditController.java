package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.AuthenticationAuditQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/authentication")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AuthenticationAuditController {

    private final AuthenticationAuditQueryService queryService;

    /* =====================================================
       CURSOR QUERY
       ===================================================== */

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
        return ResponseEntity.ok(
                queryService.query(
                        from,
                        to,
                        correlationId,
                        username,
                        subjectId,
                        result,
                        cursorTimestamp,
                        cursorId,
                        size
                )
        );
    }

    /* =====================================================
       VERIFY
       ===================================================== */

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

    /* =====================================================
       EXPORT JSONL (SEALED)
       ===================================================== */

    @GetMapping("/export")
    public void exportJsonl(
            HttpServletResponse response,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        queryService.streamForensicExportJsonl(
                response,
                from,
                to
        );
    }

    /* =====================================================
       EXPORT CSV
       ===================================================== */

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