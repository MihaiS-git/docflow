package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Admin audit metadata for purging unactivated invited users.
 * Aggregate-only (no per-user rows), to avoid audit noise.
 */
public record InvitePurgeAuditMetadata(
        @JsonProperty("candidateCount") int candidateCount,
        @JsonProperty("matchedLockedCount") int matchedLockedCount,
        @JsonProperty("deletedCount") int deletedCount
) implements AdminAuditMetadata {
}
