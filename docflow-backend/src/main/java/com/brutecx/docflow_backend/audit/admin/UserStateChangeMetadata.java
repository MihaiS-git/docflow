package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserStateChangeMetadata(
        @JsonProperty("reason") UserStateChangeReason reason,
        @JsonProperty("comment") String comment
) implements AdminAuditMetadata {
}
