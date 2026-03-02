package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record UserDeletionAuditMetadata(
        @JsonProperty("targetUserId") UUID targetUserId,
        @JsonProperty("reason") String reason
) implements AdminAuditMetadata {
}