package com.brutecx.docflow_backend.domain.user;

public record CurrentUserResult(
        CurrentUserState state,
        User user // nullable unless ACTIVE
) {
}
