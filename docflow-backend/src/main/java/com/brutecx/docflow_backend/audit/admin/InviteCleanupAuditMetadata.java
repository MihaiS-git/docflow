package com.brutecx.docflow_backend.audit.admin;

public record InviteCleanupAuditMetadata(
        int deletedInvites,
        int deletedUsers
) implements AdminAuditMetadata {
}