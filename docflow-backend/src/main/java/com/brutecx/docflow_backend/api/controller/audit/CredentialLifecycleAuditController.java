package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.domain.audit.CredentialLifecycleAuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/audit/credential-lifecycle")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class CredentialLifecycleAuditController {

    private final CredentialLifecycleAuditQueryService queryService;

    @GetMapping
    public ResponseEntity<Page<CredentialLifecycleAuditEvent>> query(
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

            Pageable pageable
    ) {
        return ResponseEntity.ok(
                queryService.query(from, to, correlationId, subjectExternalId, pageable)
        );
    }
}
