package com.brutecx.docflow_backend.security.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoleChangeMetadata(
        @JsonProperty("roleName") String roleName,
        @JsonProperty("comment") String comment
) implements AdminAuditMetadata {
}
