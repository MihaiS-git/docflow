package com.brutecx.docflow_backend.audit.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationFailureMetadata(
        @JsonProperty("failureReason") AuthenticationFailureReason failureReason,
        @JsonProperty("idpError") String idpError
) implements AuthenticationAuditMetadata {
}
