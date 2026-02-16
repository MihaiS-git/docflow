// src/main/java/com/brutecx/docflow_backend/api/controller/audit/RbacDeniedAuditController.java
package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditForensicExportDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.RbacDeniedAuditQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/rbac-denied")
@RequiredArgsConstructor
@Validated
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class RbacDeniedAuditController {

    private final RbacDeniedAuditQueryService queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<RbacDeniedAuditCursorPageDTO> query(
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
    public ResponseEntity<RbacDeniedAuditVerificationResultDTO> verify(
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rbac-denied-export.jsonl\"");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            queryService.exportForensic(
                    from,
                    to,
                    correlationId,
                    subjectId,
                    dto -> writeJsonlLine(w, dto)
            );

            w.flush();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream RBAC denied forensic export", ex);
        }
    }

    private void writeJsonlLine(PrintWriter w, RbacDeniedAuditForensicExportDTO dto) {
        try {
            w.println(objectMapper.writeValueAsString(dto));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize export record", ex);
        }
    }
}
