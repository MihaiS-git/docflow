package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InviteAuditMetadata(
        @JsonProperty("invitedEmail") String invitedEmail,
        @JsonProperty("inviteId") String inviteId,
        @JsonProperty("outcome") InviteOutcome outcome,
        @JsonProperty("failureReason") String failureReason
) implements AdminAuditMetadata {
}
