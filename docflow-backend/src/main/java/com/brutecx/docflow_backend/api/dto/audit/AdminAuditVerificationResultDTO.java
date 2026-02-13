package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record AdminAuditVerificationResultDTO(
        boolean valid,
        long verifiedCount,
        UUID failedEventId,
        String failureReason
) {
    public static AdminAuditVerificationResultDTO success(long count) {
        return new AdminAuditVerificationResultDTO(true, count, null, null);
    }

    public static AdminAuditVerificationResultDTO failure(
            long count,
            UUID eventId,
            String reason
    ) {
        return new AdminAuditVerificationResultDTO(false, count, eventId, reason);
    }
}
