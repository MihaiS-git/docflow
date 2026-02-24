package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLegalHoldDTO(
        UUID id,
        String streamName,
        UUID eventId,
        String correlationId,
        String caseReferenceId,
        String reason,
        String createdBy,
        Instant createdAt,
        boolean active
) {}