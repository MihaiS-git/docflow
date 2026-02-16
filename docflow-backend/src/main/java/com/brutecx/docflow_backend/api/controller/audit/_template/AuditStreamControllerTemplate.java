// src/main/java/com/brutecx/docflow_backend/api/controller/audit/_template/AuditStreamControllerTemplate.java
package com.brutecx.docflow_backend.api.controller.audit._template;

import com.brutecx.docflow_backend.api.controller.audit.support.AuditExportSupport;
import com.brutecx.docflow_backend.domain.audit._template.AuditStreamQueryServiceTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/<stream-slug>")
@RequiredArgsConstructor
@Validated
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','AUDITOR')") // per-stream policy
public class AuditStreamControllerTemplate {

    private final AuditStreamQueryServiceTemplate queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Object /* CursorPageDTO */> query(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant cursorTimestamp,

            @RequestParam(required = false)
            UUID cursorId,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ResponseEntity.ok(
                queryService.query(from, to, correlationId, cursorTimestamp, cursorId, size)
        );
    }

    @GetMapping("/verify")
    public ResponseEntity<Object /* VerificationResultDTO */> verify(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(queryService.verify(from, to));
    }

    @GetMapping(value = "/export", produces = AuditExportSupport.NDJSON)
    public void exportJsonl(
            HttpServletResponse response,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId
    ) {
        AuditExportSupport.prepareNdjson(response, "<stream-slug>-export.jsonl");

        try (PrintWriter w = AuditExportSupport.newUtf8Writer(response)) {

            queryService.streamForensicExportJsonl(
                    from,
                    to,
                    correlationId,
                    dto -> AuditExportSupport.writeJsonlLine(w, objectMapper, dto)
            );

            w.flush();
        }
    }

    @GetMapping(value = "/export/csv", produces = AuditExportSupport.CSV)
    public void exportCsv(
            HttpServletResponse response,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId
    ) {
        AuditExportSupport.prepareCsv(response, "<stream-slug>-export.csv");

        try (PrintWriter w = AuditExportSupport.newUtf8Writer(response)) {

            w.println("<csv-header-1>,<csv-header-2>,<csv-header-...>");

            queryService.streamForensicExportJsonl(
                    from,
                    to,
                    correlationId,
                    dto -> w.println(String.join(",",
                            AuditExportSupport.csv("<dto.field1()>"),
                            AuditExportSupport.csv("<dto.field2()>")
                    ))
            );

            w.flush();
        }
    }
}
