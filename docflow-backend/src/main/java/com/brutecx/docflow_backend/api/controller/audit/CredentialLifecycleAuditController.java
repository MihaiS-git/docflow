package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.CredentialLifecycleAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.domain.audit.CredentialLifecycleAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/credential-lifecycle")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class CredentialLifecycleAuditController {

    private final CredentialLifecycleAuditQueryService queryService;

    /* =========================
       QUERY
       ========================= */

    @GetMapping
    public ResponseEntity<Page<CredentialLifecycleAuditDTO>> query(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            String subjectExternalId,

            @RequestParam(required = false)
            AuditResult result,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant cursorTimestamp,

            @RequestParam(required = false)
            UUID cursorId,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "timestamp")
            String sort,

            @RequestParam(defaultValue = "DESC")
            Sort.Direction direction
    ) {
        return ResponseEntity.ok(
                queryService.query(
                        from,
                        to,
                        correlationId,
                        subjectExternalId,
                        result,
                        cursorTimestamp,
                        cursorId,
                        page,
                        size,
                        sort,
                        direction
                )
        );
    }

    /* =========================
       EXPORT CSV (requires from/to)
       ========================= */

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
            String subjectExternalId,

            @RequestParam(required = false)
            AuditResult result
    ) {

        StreamingResponseBody body = outputStream -> {
            PrintWriter w = new PrintWriter(outputStream);

            w.println("timestamp,id,subjectExternalId,eventType,result,correlationId,eventFingerprint,eventHash");

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectExternalId,
                    result,
                    (CredentialLifecycleAuditEvent e) -> w.println(String.join(",",
                            e.getTimestamp().toString(),
                            e.getId().toString(),
                            safeCsv(e.getSubjectExternalId()),
                            e.getEventType().name(),
                            e.getResult().name(),
                            safeCsv(e.getCorrelationId()),
                            safeCsv(e.getEventFingerprint()),
                            safeCsv(e.getEventHash())
                    ))
            );

            w.flush();
        };

        return ResponseEntity.ok(body);
    }

    /* =========================
       EXPORT JSONL (requires from/to)
       ========================= */

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
            String subjectExternalId,

            @RequestParam(required = false)
            AuditResult result
    ) {

        StreamingResponseBody body = outputStream -> {
            PrintWriter w = new PrintWriter(outputStream);

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectExternalId,
                    result,
                    e -> w.println(CredentialLifecycleAuditDTO.from(e))
            );

            w.flush();
        };

        return ResponseEntity.ok(body);
    }

    /* =========================
       VERIFY (requires from/to)
       ========================= */

    @GetMapping("/verify")
    public ResponseEntity<CredentialLifecycleAuditVerificationResultDTO> verify(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            String subjectExternalId
    ) {
        return ResponseEntity.ok(
                queryService.verify(from, to, correlationId, subjectExternalId)
        );
    }

    private static String safeCsv(String v) {
        if (v == null) return "";
        // Minimal CSV safety without changing semantics (no quoting rules needed for your current fields)
        return v.replace("\n", " ").replace("\r", " ").replace(",", " ");
    }
}
