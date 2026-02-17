package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.AdminAuditQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
        return ResponseEntity.ok(queryService.verify(from, to, tenantId));
    }

    @GetMapping(value = "/export", produces = "application/json")
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
        response.setContentType("application/json");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"admin-audit-export.jsonl\""
        );
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        queryService.streamForensicExportJsonl(response, from, to, tenantId);
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
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
        response.setContentType("text/csv");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"admin-audit-export.csv\""
        );
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        queryService.streamForensicExportCsv(response, from, to, tenantId);
    }
}
