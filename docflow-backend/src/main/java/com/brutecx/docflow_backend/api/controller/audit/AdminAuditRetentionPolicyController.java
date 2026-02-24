package com.brutecx.docflow_backend.api.controller.audit;

import com.brutecx.docflow_backend.api.dto.audit.AuditRetentionPolicyDTO;
import com.brutecx.docflow_backend.api.dto.audit.UpsertAuditRetentionPolicyRequest;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionPolicy;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionPolicyManagementService;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionPolicyRepository;
import com.brutecx.docflow_backend.domain.audit.retention.AuditRetentionStreamRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/audit/retention")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditRetentionPolicyController {

    private final AuditRetentionPolicyRepository policyRepository;
    private final AuditRetentionPolicyManagementService managementService;

    @GetMapping
    public ResponseEntity<List<AuditRetentionPolicyDTO>> listAll() {
        List<AuditRetentionPolicy> existing = policyRepository.findAll();

        // Build stable mapping: DB policy by streamName
        // (No refactor, minimal local approach)
        java.util.Map<String, AuditRetentionPolicy> byStream = new java.util.HashMap<>();
        for (AuditRetentionPolicy p : existing) {
            byStream.put(p.getStreamName(), p);
        }

        List<AuditRetentionPolicyDTO> out = new ArrayList<>();

        for (String streamName : AuditRetentionStreamRegistry.streamNames()) {
            AuditRetentionPolicy p = byStream.get(streamName);
            if (p == null) {
                out.add(new AuditRetentionPolicyDTO(
                        streamName,
                        null,
                        false,
                        null,
                        null
                ));
            } else {
                out.add(toDto(p));
            }
        }

        // Keep response deterministic (registry is LinkedHashMap-backed, but streamNames() returns keySet of copyOf)
        // Sort by streamName to remove any accidental iteration differences.
        out.sort(Comparator.comparing(AuditRetentionPolicyDTO::streamName));

        return ResponseEntity.ok(out);
    }

    @PutMapping("/{streamName}")
    public ResponseEntity<AuditRetentionPolicyDTO> upsert(
            @PathVariable String streamName,
            @Valid @RequestBody UpsertAuditRetentionPolicyRequest request
    ) {
        AuditRetentionPolicy saved =
                managementService.upsert(
                        streamName,
                        request.retentionDays(),
                        request.archiveEnabled()
                );

        return ResponseEntity.ok(toDto(saved));
    }

    private static AuditRetentionPolicyDTO toDto(AuditRetentionPolicy p) {
        return new AuditRetentionPolicyDTO(
                p.getStreamName(),
                p.getRetentionDays(),
                p.isArchiveEnabled(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

}