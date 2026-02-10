package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Admin audit metadata for tenant mutations.
 * Keep it compact and typed; details are for operators, not for business logic.
 */
public record TenantAuditMetadata(
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("operation") String operation,
        @JsonProperty("comment") String comment
) implements AdminAuditMetadata {
}