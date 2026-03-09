package com.brutecx.docflow_backend.audit.lifecycle;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionRevocationMetadata(
        @JsonProperty("targetSubjectId") String targetSubjectId,
        @JsonProperty("actorSubjectId") String actorSubjectId,
        @JsonProperty("revokedCount") int revokedCount
) implements LifecycleAuditMetadata {
}