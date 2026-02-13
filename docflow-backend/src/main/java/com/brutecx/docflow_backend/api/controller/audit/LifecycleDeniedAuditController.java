package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.LifecycleDeniedAuditDTO;
import com.brutecx.docflow_backend.domain.audit.LifecycleDeniedAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/lifecycle-denied")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LifecycleDeniedAuditController {

    private final LifecycleDeniedAuditQueryService queryService;

    /* =====================================================
       QUERY
       ===================================================== */

    @GetMapping
    public ResponseEntity<Page<LifecycleDeniedAuditDTO>> query(

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

            /* ===== Cursor pagination (PRIMARY) ===== */

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant cursorTimestamp,

            @RequestParam(required = false)
            UUID cursorId,

            /* ===== Offset pagination (UI convenience) ===== */

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            /* ===== Sorting ===== */

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
                        subjectId,
                        cursorTimestamp,
                        cursorId,
                        page,
                        size,
                        sort,
                        direction
                )
        );
    }

    /* =====================================================
       EXPORT (CSV)
       ===================================================== */

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> exportCsv(

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

        var body = (org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody) outputStream -> {

            var writer = new java.io.PrintWriter(outputStream);

            writer.println("timestamp,subjectId,reasonCode,httpMethod,path,ip,userAgent,correlationId,eventFingerprint");

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectId,
                    e -> writer.println(String.join(",",
                            e.getTimestamp().toString(),
                            safe(e.getSubjectId()),
                            safe(e.getReasonCode()),
                            safe(e.getHttpMethod()),
                            safe(e.getPath()),
                            safe(e.getIp()),
                            safe(e.getUserAgent()),
                            safe(e.getCorrelationId()),
                            safe(e.getEventFingerprint())
                    ))
            );

            writer.flush();
        };

        return ResponseEntity.ok(body);
    }

    /* =====================================================
       VERIFY
       ===================================================== */

    @GetMapping("/verify")
    public ResponseEntity<Long> verify(

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

    private static String safe(String v) {
        if (v == null) return "";
        return v.replace("\n", " ")
                .replace("\r", " ")
                .replace(",", " ");
    }
}
