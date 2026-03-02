package com.brutecx.docflow_backend.api.dto.audit;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateLegalHoldRequestDTO(
        @NotBlank String streamName,
        UUID eventId,
        String correlationId,
        @NotBlank String caseReferenceId,
        @NotBlank String reason
) {
}