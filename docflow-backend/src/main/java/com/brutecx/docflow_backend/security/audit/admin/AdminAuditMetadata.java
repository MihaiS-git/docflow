package com.brutecx.docflow_backend.security.audit.admin;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RoleChangeMetadata.class, name = "ROLE_CHANGE"),
        @JsonSubTypes.Type(value = UserStateChangeMetadata.class, name = "USER_STATE_CHANGE")
})
public sealed interface AdminAuditMetadata
        permits RoleChangeMetadata, UserStateChangeMetadata {
}
