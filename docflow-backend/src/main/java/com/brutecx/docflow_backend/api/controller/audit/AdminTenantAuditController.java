package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditForensicDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.AdminTenantAuditQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
@RequiredArgsConstructor
@RequestMapping("/api/admin/tenants/{tenantId}/audit")
@Validated
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AdminTenantAuditController {

    private final AdminTenantAuditQueryService queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<AdminAuditDTO>> query(
            @PathVariable UUID tenantId,

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
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant cursorTimestamp,

            @RequestParam(required = false)
            UUID cursorId,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ResponseEntity.ok(
                queryService.query(
                        tenantId,
                        from,
                        to,
                        correlationId,
                        actorUserId,
                        cursorTimestamp,
                        cursorId,
                        size
                )
        );
    }

    @GetMapping(value = "/export", produces = "application/x-ndjson")
    public void exportJsonl(
            HttpServletResponse response,
            @PathVariable UUID tenantId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-ndjson");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin-tenant-audit.jsonl\"");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            queryService.exportForensic(
                    tenantId,
                    from,
                    to,
                    dto -> writeJsonlLine(w, dto)
            );

            w.flush();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream admin tenant audit forensic export", ex);
        }
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public void exportCsv(
            HttpServletResponse response,
            @PathVariable UUID tenantId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        response.setHeader(HttpHeaders.CONTENT_TYPE, "text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"admin-tenant-audit.csv\"");
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            w.println(String.join(",",
                    "stream",
                    "tenantId",
                    "timestamp",
                    "id",
                    "actorUserId",
                    "subjectId",
                    "actionType",
                    "targetUserId",
                    "result",
                    "correlationId",
                    "correlationSource",
                    "executionContext",
                    "ip",
                    "userAgent",
                    "eventFingerprint",
                    "chainVersion",
                    "prevEventHash",
                    "eventHash",
                    "metadata"
            ));

            queryService.exportForensic(
                    tenantId,
                    from,
                    to,
                    dto -> w.println(String.join(",",
                            csv(dto.stream()),
                            csv(dto.tenantId()),
                            csv(dto.timestamp()),
                            csv(dto.id()),
                            csv(dto.actorUserId()),
                            csv(dto.subjectId()),
                            csv(dto.actionType()),
                            csv(dto.targetUserId()),
                            csv(dto.result()),
                            csv(dto.correlationId()),
                            csv(dto.correlationSource()),
                            csv(dto.executionContext()),
                            csv(dto.ip()),
                            csv(dto.userAgent()),
                            csv(dto.eventFingerprint()),
                            csv(Integer.toString(dto.chainVersion())),
                            csv(dto.prevEventHash()),
                            csv(dto.eventHash()),
                            csv(metadataAsJson(dto))
                    ))
            );

            w.flush();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to stream admin tenant audit CSV export", ex);
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<AuditVerificationResultDTO> verify(
            @PathVariable UUID tenantId,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(queryService.verify(tenantId, from, to));
    }

    private void writeJsonlLine(PrintWriter w, AdminAuditForensicDTO dto) {
        try {
            w.println(objectMapper.writeValueAsString(dto));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize export record", ex);
        }
    }

    private String metadataAsJson(AdminAuditForensicDTO dto) {
        if (dto.metadata() == null) return "";
        try {
            return objectMapper.writeValueAsString(dto.metadata());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize metadata for CSV export", e);
        }
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!needsQuotes) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
