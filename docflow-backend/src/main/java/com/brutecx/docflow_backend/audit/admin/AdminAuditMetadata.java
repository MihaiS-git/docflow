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
        @JsonSubTypes.Type(value = InviteCleanupAuditMetadata.class, name = "INVITE_CLEANUP"),
        @JsonSubTypes.Type(value = InviteSubjectBindingAuditMetadata.class, name = "INVITE_SUBJECT_BINDING"),
        @JsonSubTypes.Type(value = UserDeletionAuditMetadata.class, name = "USER_DELETION"),
        @JsonSubTypes.Type(value = UserStateChangeMetadata.class, name = "USER_STATE_CHANGE"),
        @JsonSubTypes.Type(value = InviteAuditMetadata.class, name = "INVITE"),
        @JsonSubTypes.Type(value = TenantAuditMetadata.class, name = "TENANT"),
        @JsonSubTypes.Type(value = TenantMembershipChangeMetadata.class, name = "TENANT_MEMBERSHIP_CHANGE"),
        @JsonSubTypes.Type(value = RetentionPolicyAuditMetadata.class, name = "RETENTION_POLICY")
})
public sealed interface AdminAuditMetadata
        permits InviteAuditMetadata,
        InviteCleanupAuditMetadata,
        InviteSubjectBindingAuditMetadata,
        InvitePurgeAuditMetadata,
        UserDeletionAuditMetadata,
        RoleChangeMetadata,
        UserStateChangeMetadata,
        TenantAuditMetadata,
        TenantMembershipChangeMetadata,
        RetentionPolicyAuditMetadata {
}
