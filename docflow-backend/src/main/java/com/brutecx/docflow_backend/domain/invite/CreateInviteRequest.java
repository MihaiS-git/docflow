package com.brutecx.docflow_backend.domain.invite;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateInviteRequest(
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