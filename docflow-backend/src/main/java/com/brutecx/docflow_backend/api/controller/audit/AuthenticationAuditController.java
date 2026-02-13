package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuthenticationAuditDTO;
import com.brutecx.docflow_backend.audit.auth.AuthenticationResult;
import com.brutecx.docflow_backend.domain.audit.AuthenticationAuditQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/authentication")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuthenticationAuditController {

    private final AuthenticationAuditQueryService queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<AuthenticationAuditDTO>> query(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            String username,

            @RequestParam(required = false)
            AuthenticationResult result,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(defaultValue = "timestamp")
            String sort,

            @RequestParam(defaultValue = "DESC")
            Sort.Direction direction,


            // Cursor is Base64URL of: "<timestamp-iso>|<uuid>"
            // If cursor is present, cursor mode is used and page is ignored; 'size' becomes 'limit'.
            @RequestParam(required = false)
            String cursor
    ) {
        if (cursor != null && !cursor.isBlank()) {
            AuthenticationAuditQueryService.Cursor decoded = decodeCursor(cursor);
            return ResponseEntity.ok(
                    queryService.queryByCursor(
                            from,
                            to,
                            correlationId,
                            username,
                            result,
                            decoded,
                            size,
                            direction
                    )
            );
        }

        return ResponseEntity.ok(
                queryService.query(
                        from,
                        to,
                        correlationId,
                        page,
                        size,
                        sort,
                        direction,
                        username,
                        result
                )
        );
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> exportEvidence(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            String correlationId,

            @RequestParam(required = false)
            String username,

            @RequestParam(required = false)
            AuthenticationResult result,

            @RequestParam(defaultValue = "csv")
            String format
    ) {
        String safeFormat = (format == null ? "csv" : format.trim().toLowerCase());
        if (!safeFormat.equals("csv") && !safeFormat.equals("jsonl")) {
            throw new IllegalArgumentException("Unsupported export format. Use 'csv' or 'jsonl'.");
        }

        String filename = "authentication-audit_" + from + "_" + to + "." + safeFormat;

        StreamingResponseBody body = outputStream -> {
            if (safeFormat.equals("csv")) {
                queryService.streamExportCsv(outputStream, from, to, correlationId, username, result);
                return;
            }
            queryService.streamExportJsonl(outputStream, objectMapper, from, to, correlationId, username, result);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        headers.set(HttpHeaders.CONTENT_ENCODING, StandardCharsets.UTF_8.name());

        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/verify")
    public ResponseEntity<AuthenticationAuditQueryService.VerificationReport> verify(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(queryService.verifyContinuity(from, to));
    }

    private AuthenticationAuditQueryService.Cursor decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid cursor format");
            Instant ts = Instant.parse(parts[0]);
            UUID id = UUID.fromString(parts[1]);
            return new AuthenticationAuditQueryService.Cursor(ts, id);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }
}
