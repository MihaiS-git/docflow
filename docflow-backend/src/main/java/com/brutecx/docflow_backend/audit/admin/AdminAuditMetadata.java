package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RoleChangeMetadata.class, name = "ROLE_CHANGE"),
        @JsonSubTypes.Type(value = UserStateChangeMetadata.class, name = "USER_STATE_CHANGE"),
        @JsonSubTypes.Type(value = InviteAuditMetadata.class, name = "INVITE"),
        @JsonSubTypes.Type(value = TenantAuditMetadata.class, name = "TENANT")
})
public sealed interface AdminAuditMetadata
        permits InviteAuditMetadata,
        InviteCleanupAuditMetadata,
        RoleChangeMetadata,
        UserStateChangeMetadata,
        TenantAuditMetadata {
}
