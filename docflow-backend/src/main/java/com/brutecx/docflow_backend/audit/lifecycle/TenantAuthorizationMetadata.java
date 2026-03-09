package com.brutecx.docflow_backend.audit.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TenantAuthorizationMetadata(
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("requiredRole") String requiredRole,
        @JsonProperty("actualRole") String actualRole,
        @JsonProperty("membershipStatus") String membershipStatus
) implements LifecycleAuditMetadata {
}