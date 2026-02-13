package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record RbacDeniedAuditVerificationResultDTO(
        boolean success,
        long verifiedCount,
        UUID failedEventId,
        String message
) {
    public static RbacDeniedAuditVerificationResultDTO success(long verifiedCount) {
        return new RbacDeniedAuditVerificationResultDTO(true, verifiedCount, null, null);
    }

    public static RbacDeniedAuditVerificationResultDTO failure(
            long verifiedCount,
            UUID failedEventId,
            String message
    ) {
        return new RbacDeniedAuditVerificationResultDTO(false, verifiedCount, failedEventId, message);
    }
}
