package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AdminAuditVerificationResultDTO;
import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import com.brutecx.docflow_backend.domain.audit.AdminTenantAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tenants/{tenantId}/audit")
public class AdminTenantAuditController {

    private final AdminTenantAuditQueryService queryService;

    /* ================= QUERY ================= */

    @GetMapping
    public ResponseEntity<Page<AdminAuditEvent>> query(
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
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "20")
            int size,
            @RequestParam(defaultValue = "DESC")
            Sort.Direction direction
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
                        page,
                        size,
                        direction
                )
        );
    }

    /* ================= EXPORT CSV ================= */

    @GetMapping(value = "/export/csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(
            @PathVariable UUID tenantId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {

        StreamingResponseBody stream = outputStream -> {

            PrintWriter writer = new PrintWriter(outputStream);
            writer.println("timestamp,id,actorUserId,tenantId,actionType,result,eventHash");

            queryService.export(
                    tenantId,
                    from,
                    to,
                    event -> writer.println(
                            String.join(",",
                                    event.getTimestamp().toString(),
                                    event.getId().toString(),
                                    event.getActorUserId().toString(),
                                    event.getTenantId().toString(),
                                    event.getActionType().name(),
                                    event.getResult().name(),
                                    event.getEventHash()
                            )
                    )
            );

            writer.flush();
        };

        return ResponseEntity.ok(stream);
    }

    /* ================= VERIFY ================= */

    @GetMapping("/verify")
    public ResponseEntity<AdminAuditVerificationResultDTO> verify(
            @PathVariable UUID tenantId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to
    ) {
        return ResponseEntity.ok(
                queryService.verify(tenantId, from, to)
        );
    }
}
