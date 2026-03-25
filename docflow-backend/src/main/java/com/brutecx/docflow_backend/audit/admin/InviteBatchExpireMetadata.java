package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InviteBatchExpireMetadata(
        @JsonProperty("expiredCount") int expiredCount
) implements AdminAuditMetadata {}