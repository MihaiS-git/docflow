package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.LifecycleDeniedAuditQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/lifecycle-denied")
@RequiredArgsConstructor
@Validated
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class LifecycleDeniedAuditController {

    private final LifecycleDeniedAuditQueryService queryService;

    @GetMapping
    public ResponseEntity<LifecycleDeniedAuditCursorPageDTO> query(
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
        return ResponseEntity.ok(queryService.verify(from, to));
    }

    @GetMapping(value = "/export", produces = "application/x-ndjson")
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
            String subjectId
    ) {
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-ndjson");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lifecycle-denied-export.jsonl\"");
        response.setCharacterEncoding("UTF-8");

        queryService.streamForensicExportJsonl(response, from, to, correlationId, subjectId);
    }
}
