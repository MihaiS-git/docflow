package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.RbacDeniedAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;
import com.brutecx.docflow_backend.domain.audit.RbacDeniedAuditQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/rbac-denied")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RbacDeniedAuditController {

    private final RbacDeniedAuditQueryService queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<RbacDeniedAuditDTO>> query(
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
            int size,

            @RequestParam(defaultValue = "DESC")
            Sort.Direction direction
    ) {
        return ResponseEntity.ok(
                queryService.query(
                        from,
                        to,
                        correlationId,
                        subjectId,
                        cursorTimestamp,
                        cursorId,
                        size,
                        direction
                )
        );
    }

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
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
        StreamingResponseBody body = outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));
            writer.println("timestamp,subjectId,httpMethod,path,ip,userAgent,correlationId,correlationSource,executionContext,result,eventFingerprint,prevEventHash,eventHash");

            queryService.export(from, to, correlationId, subjectId, e -> writer.println(toCsvRow(e)));
            writer.flush();
        };
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/export/jsonl", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public ResponseEntity<StreamingResponseBody> exportJsonl(
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
        StreamingResponseBody body = outputStream -> {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));

            queryService.export(from, to, correlationId, subjectId, e -> {
                try {
                    writer.println(objectMapper.writeValueAsString(RbacDeniedAuditDTO.from(e)));
                } catch (Exception ex) {
                    throw new IllegalStateException("Failed to serialize JSONL audit row", ex);
                }
            });

            writer.flush();
        };

        return ResponseEntity.ok(body);
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

    private static String toCsvRow(RbacDeniedAuditEvent e) {
        return String.join(",",
                csv(e.getTimestamp() != null ? e.getTimestamp().toString() : ""),
                csv(e.getSubjectId()),
                csv(e.getHttpMethod()),
                csv(e.getPath()),
                csv(e.getIp()),
                csv(e.getUserAgent()),
                csv(e.getCorrelationId()),
                csv(e.getCorrelationSource() != null ? e.getCorrelationSource().name() : ""),
                csv(e.getExecutionContext() != null ? e.getExecutionContext().name() : ""),
                csv(e.getResult() != null ? e.getResult().name() : ""),
                csv(e.getEventFingerprint()),
                csv(e.getPrevEventHash()),
                csv(e.getEventHash())
        );
    }

    private static String csv(String v) {
        if (v == null) return "";
        boolean needsQuotes = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (!needsQuotes) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
