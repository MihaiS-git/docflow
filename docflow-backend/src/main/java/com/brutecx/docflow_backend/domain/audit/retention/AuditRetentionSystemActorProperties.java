package com.brutecx.docflow_backend.domain.audit.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Validated
@ConfigurationProperties(prefix = "docflow.audit.retention")
public record AuditRetentionSystemActorProperties(
        @NotBlank String systemActorUserId
) {
    public UUID systemActorUuid() {
        return UUID.fromString(systemActorUserId.trim());
    }
}
