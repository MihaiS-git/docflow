package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.domain.audit.AdminAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.PrintWriter;
import com.brutecx.docflow_backend.api.dto.audit.AdminAuditVerificationResultDTO;


import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/admin-actions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AdminAuditQueryService queryService;

    @GetMapping
    public ResponseEntity<Page<AdminAuditDTO>> query(
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
            UUID tenantId,

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
                        actorUserId,
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
            UUID actorUserId,

            @RequestParam(required = false)
            UUID tenantId
    ) {

        StreamingResponseBody stream = outputStream -> {

            PrintWriter writer = new PrintWriter(outputStream);

            writer.println("timestamp,id,actorUserId,tenantId,actionType,result,correlationId,eventFingerprint");

            queryService.export(
                    from,
                    to,
                    correlationId,
                    actorUserId,
                    tenantId,
                    event -> writer.println(
                            String.join(",",
                                    event.getTimestamp().toString(),
                                    event.getId().toString(),
                                    event.getActorUserId().toString(),
                                    event.getTenantId().toString(),
                                    event.getActionType().name(),
                                    event.getResult().name(),
                                    event.getCorrelationId(),
                                    event.getEventFingerprint()
                            )
                    )
            );

            writer.flush();
        };

        return ResponseEntity.ok(stream);
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
            UUID actorUserId,

            @RequestParam(required = false)
            UUID tenantId
    ) {

        StreamingResponseBody stream = outputStream -> {

            PrintWriter writer = new PrintWriter(outputStream);

            queryService.export(
                    from,
                    to,
                    correlationId,
                    actorUserId,
                    tenantId,
                    event -> writer.println(AdminAuditDTO.from(event))
            );

            writer.flush();
        };

        return ResponseEntity.ok(stream);
    }

    @GetMapping("/verify")
    public ResponseEntity<AdminAuditVerificationResultDTO> verify(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            UUID tenantId
    ) {
        return ResponseEntity.ok(
                queryService.verify(from, to, tenantId)
        );
    }


}
