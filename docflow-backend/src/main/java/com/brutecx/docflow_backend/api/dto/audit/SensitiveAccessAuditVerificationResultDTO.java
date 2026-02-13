package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record SensitiveAccessAuditVerificationResultDTO(
        boolean success,
        long verifiedCount,
        UUID failedEventId,
        UUID failedTenantId,
        String message
) {
    public static SensitiveAccessAuditVerificationResultDTO success(long verifiedCount) {
        return new SensitiveAccessAuditVerificationResultDTO(
                true,
                verifiedCount,
                null,
                null,
                null
        );
    }

    public static SensitiveAccessAuditVerificationResultDTO failure(
            long verifiedCount,
            UUID failedEventId,
            UUID failedTenantId,
            String message
    ) {
        return new SensitiveAccessAuditVerificationResultDTO(
                false,
                verifiedCount,
                failedEventId,
                failedTenantId,
                message
        );
    }
}
