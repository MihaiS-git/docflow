package com.brutecx.docflow_backend.application.invite;

public record CleanupResult(
        int deletedInvites,
        int deletedUsers
) {
}