package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.SensitiveAccessAuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.SensitiveAccessAuditQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/sensitive-access")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SensitiveAccessAuditController {

    private final SensitiveAccessAuditQueryService queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<SensitiveAccessAuditDTO>> query(
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
                        tenantId,
                        actorUserId,
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
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId,

            @RequestParam(required = false)
            UUID actorUserId
    ) {
        StreamingResponseBody body = outputStream -> {
            PrintWriter writer = new PrintWriter(outputStream);

            writer.println(String.join(",",
                    "timestamp",
                    "actorUserId",
                    "actorExternalSubjectId",
                    "tenantId",
                    "subjectType",
                    "subjectId",
                    "resource",
                    "action",
                    "resourcePath",
                    "correlationId",
                    "correlationSource",
                    "executionContext",
                    "result",
                    "ip",
                    "userAgent",
                    "reasonCode",
                    "reasonDetail",
                    "dataClassification",
                    "eventFingerprint"
            ));

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectId,
                    tenantId,
                    actorUserId,
                    e -> writer.println(toCsvLine(SensitiveAccessAuditDTO.from(e)))
            );

            writer.flush();
        };

        return ResponseEntity.ok(body);
    }

    @GetMapping(
            value = "/export/jsonl",
            produces = org.springframework.http.MediaType.APPLICATION_NDJSON_VALUE
    )
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
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId,

            @RequestParam(required = false)
            UUID actorUserId
    ) {
        StreamingResponseBody body = outputStream -> {
            PrintWriter writer = new PrintWriter(outputStream);

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectId,
                    tenantId,
                    actorUserId,
                    e -> {
                        try {
                            // JSONL of DTO (not entity)
                            String json = objectMapper.writeValueAsString(SensitiveAccessAuditDTO.from(e));
                            writer.println(json);
                        } catch (Exception ex) {
                            throw new IllegalStateException("Failed to write JSONL", ex);
                        }
                    }
            );

            writer.flush();
        };

        return ResponseEntity.ok(body);
    }

    @GetMapping("/verify")
    public ResponseEntity<SensitiveAccessAuditVerificationResultDTO> verify(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(queryService.verify(from, to));
    }

    private static String toCsvLine(SensitiveAccessAuditDTO d) {
        return String.join(",",
                csv(d.timestamp()),
                csv(d.actorUserId()),
                csv(d.actorExternalSubjectId()),
                csv(d.tenantId()),
                csv(d.subjectType() != null ? d.subjectType().name() : ""),
                csv(d.subjectId()),
                csv(d.resource()),
                csv(d.action()),
                csv(d.resourcePath()),
                csv(d.correlationId()),
                csv(d.correlationSource() != null ? d.correlationSource().name() : ""),
                csv(d.executionContext() != null ? d.executionContext().name() : ""),
                csv(d.result() != null ? d.result().name() : ""),
                csv(d.ip()),
                csv(d.userAgent()),
                csv(d.reasonCode()),
                csv(d.reasonDetail()),
                csv(d.dataClassification() != null ? d.dataClassification().name() : ""),
                csv(d.eventFingerprint())
        );
    }

    private static String csv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        // RFC4180-ish minimal escaping
        boolean mustQuote =
                s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (!mustQuote) {
            return s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
