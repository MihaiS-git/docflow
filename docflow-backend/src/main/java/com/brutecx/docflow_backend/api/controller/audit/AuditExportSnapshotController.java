package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditExportSnapshotCursorPageDTO;
import com.brutecx.docflow_backend.api.dto.audit.AuditExportSnapshotDTO;
import com.brutecx.docflow_backend.domain.audit.export.AuditExportSnapshotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit/exports")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('AUDITOR')")
public class AuditExportSnapshotController {

    private final AuditExportSnapshotQueryService queryService;

    @GetMapping
    public ResponseEntity<AuditExportSnapshotCursorPageDTO> query(
            @RequestParam(required = false) UUID snapshotId,
            @RequestParam(required = false) String stream,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant exportFromTs,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant exportToTs,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorCreatedAt,
            @RequestParam(required = false) UUID cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(queryService.query(
                snapshotId,
                stream,
                tenantId,
                createdBy,
                createdFrom,
                createdTo,
                exportFromTs,
                exportToTs,
                cursorCreatedAt,
                cursorId,
                size
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditExportSnapshotDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getById(id));
    }
}
