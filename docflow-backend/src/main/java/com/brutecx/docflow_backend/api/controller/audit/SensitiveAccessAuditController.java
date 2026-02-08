package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.admin.audit.SensitiveAccessAuditDTO;
import com.brutecx.docflow_backend.domain.audit.SensitiveAccessAuditQueryService;
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
@RequestMapping("/api/audit/sensitive-access")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SensitiveAccessAuditController {

    private final SensitiveAccessAuditQueryService queryService;

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

            Pageable pageable
    ) {
        return ResponseEntity.ok(
                queryService.query(from, to, correlationId, subjectId, tenantId, actorUserId, pageable)
        );
    }
}
