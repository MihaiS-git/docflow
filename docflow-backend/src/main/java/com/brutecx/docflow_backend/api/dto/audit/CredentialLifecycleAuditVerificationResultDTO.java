package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record CredentialLifecycleAuditVerificationResultDTO(
        boolean valid,
        long verifiedCount,
        UUID failedEventId,
        String failureReason
) {
    public static CredentialLifecycleAuditVerificationResultDTO success(long count) {
        return new CredentialLifecycleAuditVerificationResultDTO(true, count, null, null);
    }

    public static CredentialLifecycleAuditVerificationResultDTO failure(
            long count,
            UUID eventId,
            String reason
    ) {
        return new CredentialLifecycleAuditVerificationResultDTO(false, count, eventId, reason);
    }
}
