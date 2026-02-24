package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;

public record AuditRetentionPolicyDTO(
        String streamName,
        Integer retentionDays,
        boolean archiveEnabled,
        Instant createdAt,
        Instant updatedAt
) {}