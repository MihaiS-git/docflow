package com.brutecx.docflow_backend.api.dto.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpsertAuditRetentionPolicyRequest(
        @Min(value = 1, message = "retentionDays must be >= 1")
        @Max(value = 3650, message = "retentionDays too large" )
        int retentionDays,
        boolean archiveEnabled
) {}