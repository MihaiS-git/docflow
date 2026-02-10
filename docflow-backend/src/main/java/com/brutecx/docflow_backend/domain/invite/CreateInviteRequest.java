package com.brutecx.docflow_backend.domain.invite;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInviteRequest(
        @NotNull
        UUID targetTenantId,

        @NotBlank
        @Email
        String email,

        @NotBlank
        String firstName,

        @NotBlank
        String lastName,

        String jobTitle,

        String department
) {
}