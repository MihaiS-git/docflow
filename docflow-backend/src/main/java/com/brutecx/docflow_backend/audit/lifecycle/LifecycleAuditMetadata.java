package com.brutecx.docflow_backend.audit.lifecycle;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TenantAuthorizationMetadata.class, name = "TENANT_AUTHZ"),
        @JsonSubTypes.Type(value = SessionRevocationMetadata.class, name = "SESSION_REVOCATION")
})
public sealed interface LifecycleAuditMetadata
        permits SessionRevocationMetadata,
        TenantAuthorizationMetadata {
}