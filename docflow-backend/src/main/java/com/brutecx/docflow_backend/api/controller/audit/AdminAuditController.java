package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.admin.audit.AdminAuditDTO;
import com.brutecx.docflow_backend.domain.admin.audit.AdminAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

            Pageable pageable
    ) {
        return ResponseEntity.ok(
                queryService.query(from, to, correlationId, actorUserId, tenantId, pageable)
        );
    }
}
