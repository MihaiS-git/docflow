package com.brutecx.docflow_backend.audit.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationSuccessMetadata(
        @JsonProperty("idp") String idp
) implements AuthenticationAuditMetadata {
}
