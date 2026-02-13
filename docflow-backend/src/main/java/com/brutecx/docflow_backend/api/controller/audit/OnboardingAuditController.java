package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditDTO;
import com.brutecx.docflow_backend.api.dto.audit.OnboardingAuditVerificationResultDTO;
import com.brutecx.docflow_backend.domain.audit.OnboardingAuditQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/onboarding")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OnboardingAuditController {

    private final OnboardingAuditQueryService queryService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<OnboardingAuditDTO>> query(

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

            /* ===== Cursor pagination (PRIMARY) ===== */

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant cursorTimestamp,

            @RequestParam(required = false)
            UUID cursorId,

            /* ===== Offset pagination (UI fallback) ===== */

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            /* ===== Sorting ===== */

            @RequestParam(defaultValue = "timestamp")
            String sort,

            @RequestParam(defaultValue = "DESC")
            org.springframework.data.domain.Sort.Direction direction
    ) {

        return ResponseEntity.ok(
                queryService.query(
                        from,
                        to,
                        correlationId,
                        subjectId,
                        tenantId,
                        cursorTimestamp,
                        cursorId,
                        page,
                        size,
                        sort,
                        direction
                )
        );
    }

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
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId
    ) {

        var body = (org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody) outputStream -> {

            var writer = new java.io.PrintWriter(outputStream);

            writer.println("timestamp,actorUserId,subjectId,tenantId,inviteId,result,outcome,correlationId,eventFingerprint,eventHash");

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectId,
                    tenantId,
                    e -> writer.println(String.join(",",
                            e.getTimestamp().toString(),
                            safe(e.getActorUserId()),
                            safe(e.getSubjectId()),
                            safe(e.getTenantId()),
                            safe(e.getInviteId()),
                            e.getResult().name(),
                            e.getOutcome().name(),
                            safe(e.getCorrelationId()),
                            safe(e.getEventFingerprint()),
                            safe(e.getEventHash())
                    ))
            );

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
            String subjectId,

            @RequestParam(required = false)
            UUID tenantId
    ) {

        var body = (org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody) outputStream -> {

            var writer = new java.io.PrintWriter(outputStream);

            queryService.export(
                    from,
                    to,
                    correlationId,
                    subjectId,
                    tenantId,
                    e -> {
                        try {
                            writer.println(objectMapper.writeValueAsString(OnboardingAuditDTO.from(e)));
                        } catch (Exception ex) {
                            throw new IllegalStateException("Cannot serialize onboarding audit DTO", ex);
                        }
                    }
            );

            writer.flush();
        };

        return ResponseEntity.ok(body);
    }

    @GetMapping("/verify")
    public ResponseEntity<OnboardingAuditVerificationResultDTO> verify(

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

    private static String safe(Object v) {
        if (v == null) return "";
        return v.toString()
                .replace("\n", " ")
                .replace("\r", " ")
                .replace(",", " ");
    }

}
