package com.brutecx.docflow_backend.audit.admin;

import com.brutecx.docflow_backend.domain.tenant.MembershipStatus;
import com.brutecx.docflow_backend.domain.tenant.TenantRole;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Admin audit metadata for tenant membership mutations.
 * Compliance fields:
 * - tenantId
 * - targetUserId
 * - old/new role
 * - old/new status
 * - operation + comment
 */
public record TenantMembershipChangeMetadata(
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("targetUserId") String targetUserId,
        @JsonProperty("operation") String operation,
        @JsonProperty("oldRole") TenantRole oldRole,
        @JsonProperty("newRole") TenantRole newRole,
        @JsonProperty("oldStatus") MembershipStatus oldStatus,
        @JsonProperty("newStatus") MembershipStatus newStatus,
        @JsonProperty("comment") String comment
) implements AdminAuditMetadata {
}
