package com.brutecx.docflow_backend.audit.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Metadata payload for authentication audit events.
 * Extensible and version-safe.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AuthenticationFailureMetadata.class, name = "AUTH_FAILURE"),
        @JsonSubTypes.Type(value = AuthenticationSuccessMetadata.class, name = "AUTH_SUCCESS"),
        @JsonSubTypes.Type(value = LogoutMetadata.class, name = "LOGOUT")
})
public sealed interface AuthenticationAuditMetadata
        permits AuthenticationFailureMetadata,
        AuthenticationSuccessMetadata,
        LogoutMetadata {
}
