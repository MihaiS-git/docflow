package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record UnauthenticatedAccessAuditVerificationResultDTO(
        boolean success,
        long verifiedCount,
        UUID failedEventId,
        String error
) {
    public static UnauthenticatedAccessAuditVerificationResultDTO success(long count) {
        return new UnauthenticatedAccessAuditVerificationResultDTO(true, count, null, null);
    }

    public static UnauthenticatedAccessAuditVerificationResultDTO failure(
            long count,
            UUID failedId,
            String error
    ) {
        return new UnauthenticatedAccessAuditVerificationResultDTO(false, count, failedId, error);
    }
}
