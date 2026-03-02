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

import java.util.*;

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

        Map<String, AuditRetentionPolicy> byStream = new HashMap<>();
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
                out.add(mapToDto(p));
            }
        }
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