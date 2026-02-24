package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Admin audit metadata for invite operations.
 * Outcome is encoded via AdminAuditActionType.
 */
public record InviteAuditMetadata(
        @JsonProperty("invitedEmail") String invitedEmail,
        @JsonProperty("inviteId") String inviteId
) implements AdminAuditMetadata {
}