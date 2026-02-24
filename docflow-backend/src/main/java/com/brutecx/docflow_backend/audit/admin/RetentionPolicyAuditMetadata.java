package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Admin audit metadata for retention policy changes.
 * Captures full before/after state for compliance traceability.
 */
public record RetentionPolicyAuditMetadata(
        @JsonProperty("streamName") String streamName,
        @JsonProperty("oldRetentionDays") Integer oldRetentionDays,
        @JsonProperty("newRetentionDays") Integer newRetentionDays,
        @JsonProperty("oldArchiveEnabled") Boolean oldArchiveEnabled,
        @JsonProperty("newArchiveEnabled") Boolean newArchiveEnabled
) implements AdminAuditMetadata {
}