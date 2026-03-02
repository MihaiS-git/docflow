package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * + * Admin audit metadata for completing an invite by binding a local user to an IdP subject.
 * + *
 * + * NOTE:
 * + *  - Do not store raw email here if you don't already store it in audit. Prefer emailHash.
 * +
 */
public record InviteSubjectBindingAuditMetadata(
        @JsonProperty("targetUserId") UUID targetUserId,
        @JsonProperty("subjectId") String subjectId,
        @JsonProperty("emailHash") String emailHash
) implements AdminAuditMetadata {
}
