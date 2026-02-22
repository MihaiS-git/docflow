package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.AdminAuditQueryService;
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
@RequestMapping("/api/audit/admin-actions")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AdminAuditController {

    private final AdminAuditQueryService queryService;

    /* =====================================================
       CURSOR QUERY
       ===================================================== */

    @GetMapping
    public ResponseEntity<AdminAuditCursorPageDTO> query(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            UUID actorUserId,

            @RequestParam(required = false)
            UUID tenantId,

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
                        actorUserId,
                        tenantId,
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
            Instant to,

            @RequestParam(required = false)
            UUID tenantId
    ) {
        return ResponseEntity.ok(
                queryService.verify(from, to, tenantId)
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
            Instant to,

            @RequestParam(required = false)
            UUID tenantId
    ) {
        // IMPORTANT:
        // DO NOT set Content-Type here.
        // DO NOT declare produces.
        // Let the service control headers.
        // This allows proper JSON error handling.

        queryService.streamForensicExportJsonl(
                response,
                from,
                to,
                tenantId
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
            Instant to,

            @RequestParam(required = false)
            UUID tenantId
    ) {
        // Same principle: let service manage headers.

        queryService.streamForensicExportCsv(
                response,
                from,
                to,
                tenantId
        );
    }
}