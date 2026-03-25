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
        @JsonSubTypes.Type(value = InviteSubjectBindingAuditMetadata.class, name = "INVITE_SUBJECT_BINDING"),
        @JsonSubTypes.Type(value = UserDeletionAuditMetadata.class, name = "USER_DELETION"),
        @JsonSubTypes.Type(value = UserStateChangeMetadata.class, name = "USER_STATE_CHANGE"),
        @JsonSubTypes.Type(value = InviteAuditMetadata.class, name = "INVITE"),
        @JsonSubTypes.Type(value = InviteBatchExpireMetadata.class, name = "INVITE_BATCH_EXPIRE"),
        @JsonSubTypes.Type(value = TenantAuditMetadata.class, name = "TENANT"),
        @JsonSubTypes.Type(value = TenantMembershipChangeMetadata.class, name = "TENANT_MEMBERSHIP_CHANGE"),
        @JsonSubTypes.Type(value = RetentionPolicyAuditMetadata.class, name = "RETENTION_POLICY")
})
public sealed interface AdminAuditMetadata
        permits InviteAuditMetadata,
        InviteSubjectBindingAuditMetadata,
        InvitePurgeAuditMetadata,
        UserDeletionAuditMetadata,
        RoleChangeMetadata,
        UserStateChangeMetadata,
        InviteBatchExpireMetadata,
        TenantAuditMetadata,
        TenantMembershipChangeMetadata,
        RetentionPolicyAuditMetadata {
}
