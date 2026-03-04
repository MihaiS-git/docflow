package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditRetentionPolicyDTO;
import com.brutecx.docflow_backend.api.dto.audit.UpsertAuditRetentionPolicyRequest;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionPolicy;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionPolicyManagementService;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionStreamRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/audit/retention")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditRetentionPolicyController {

    private final AuditRetentionPolicyManagementService managementService;

    @GetMapping
    public ResponseEntity<List<AuditRetentionPolicyDTO>> listAll() {
        List<AuditRetentionPolicy> existing = managementService.listAll();

        Map<String, AuditRetentionPolicy> byStream = new HashMap<>();
        for (AuditRetentionPolicy p : existing) {
            if (p == null) continue;
            String s = p.getStreamName();
            if (s == null || s.isBlank()) continue;
            byStream.put(s.trim().toUpperCase(Locale.ROOT), p);
        }

        List<AuditRetentionPolicyDTO> out = new ArrayList<>();

        for (String streamName : AuditRetentionStreamRegistry.STREAM_NAMES) {
            AuditRetentionPolicy p = byStream.get(streamName);
            if (p == null) {
                AuditRetentionStreamRegistry.RetentionDefault d =
                        AuditRetentionStreamRegistry.defaultFor(streamName);

                out.add(new AuditRetentionPolicyDTO(
                        streamName,
                        d.retentionDays(),
                        d.archiveEnabled(),
                        null,
                        null
                ));
            } else {
                out.add(mapToDto(p));
            }
        }

        out.sort(Comparator.comparing(AuditRetentionPolicyDTO::streamName));
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{streamName}")
    public ResponseEntity<AuditRetentionPolicyDTO> upsert(
            @PathVariable @NotBlank String streamName,
            @Valid @RequestBody UpsertAuditRetentionPolicyRequest request
    ) {
        AuditRetentionPolicy saved =
                managementService.upsert(
                        streamName.trim().toUpperCase(Locale.ROOT),
                        request.retentionDays(),
                        request.archiveEnabled()
                );
        return ResponseEntity.ok(mapToDto(saved));
    }

    private static AuditRetentionPolicyDTO mapToDto(AuditRetentionPolicy p) {
        return new AuditRetentionPolicyDTO(
                p.getStreamName(),
                p.getRetentionDays(),
                p.isArchiveEnabled(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}