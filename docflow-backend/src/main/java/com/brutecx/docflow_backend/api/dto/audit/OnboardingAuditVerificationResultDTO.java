package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record OnboardingAuditVerificationResultDTO(
        boolean success,
        long verifiedCount,
        UUID failedEventId,
        String message
) {

    public static OnboardingAuditVerificationResultDTO success(long verifiedCount) {
        return new OnboardingAuditVerificationResultDTO(
                true,
                verifiedCount,
                null,
                null
        );
    }

    public static OnboardingAuditVerificationResultDTO failure(
            long verifiedCount,
            UUID failedEventId,
            String message
    ) {
        return new OnboardingAuditVerificationResultDTO(
                false,
                verifiedCount,
                failedEventId,
                message
        );
    }
}
