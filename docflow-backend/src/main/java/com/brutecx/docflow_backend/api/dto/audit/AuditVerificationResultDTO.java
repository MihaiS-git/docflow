package com.brutecx.docflow_backend.api.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditVerificationResultDTO(
        boolean valid,
        long verifiedCount,
        UUID failedEventId,
        String failureReason,

        Instant cursorTimestamp,
        UUID cursorId
) {

    public static AuditVerificationResultDTO success(long count) {
        return new AuditVerificationResultDTO(true, count, null, null, null, null);
    }

    public static AuditVerificationResultDTO failure(
            long count,
            UUID eventId,
            String reason
    ) {
        return new AuditVerificationResultDTO(false, count, eventId, reason, null, null);
    }

    public static AuditVerificationResultDTO truncated(
            long count,
            Instant cursorTs,
            UUID cursorId
    ) {
        return new AuditVerificationResultDTO(
                false,
                count,
                null,
                "VERIFICATION_LIMIT_REACHED",
                cursorTs,
                cursorId
        );
    }
}