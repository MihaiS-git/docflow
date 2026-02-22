package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditCursorPageDTO;
import com.brutecx.docflow_backend.domain.audit.SensitiveAccessAuditQueryService;
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
@RequestMapping("/api/audit/sensitive-access")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class SensitiveAccessAuditController {

    private final SensitiveAccessAuditQueryService queryService;

    /* =====================================================
       CURSOR QUERY
       ===================================================== */

    @GetMapping
    public ResponseEntity<SensitiveAccessAuditCursorPageDTO> query(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId,

            @RequestParam(required = false)
            UUID actorUserId,

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
                        subjectId,
                        tenantId,
                        actorUserId,
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
       SEALED JSONL EXPORT
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
            String correlationId,

            @RequestParam(required = false)
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId,

            @RequestParam(required = false)
            UUID actorUserId
    ) {
        queryService.streamForensicExportJsonl(
                response,
                from,
                to,
                correlationId,
                subjectId,
                tenantId,
                actorUserId
        );
    }

    /* =====================================================
       CSV EXPORT
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
            String correlationId,

            @RequestParam(required = false)
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId,

            @RequestParam(required = false)
            UUID actorUserId
    ) {
        queryService.streamForensicExportCsv(
                response,
                from,
                to,
                correlationId,
                subjectId,
                tenantId,
                actorUserId
        );
    }
}