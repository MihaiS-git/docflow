package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record AuditVerificationResultDTO(
        boolean valid,
        long verifiedCount,
        UUID failedEventId,
        String failureReason
) {
    public static AuditVerificationResultDTO success(long count) {
        return new AuditVerificationResultDTO(true, count, null, null);
    }

    public static AuditVerificationResultDTO failure(
            long count,
            UUID eventId,
            String reason
    ) {
        return new AuditVerificationResultDTO(false, count, eventId, reason);
    }
}
