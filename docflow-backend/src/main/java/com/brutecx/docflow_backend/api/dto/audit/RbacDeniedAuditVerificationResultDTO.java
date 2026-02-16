package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record RbacDeniedAuditVerificationResultDTO(
        boolean success,
        long verified,
        UUID failingEventId,
        String reason
) {
    public static RbacDeniedAuditVerificationResultDTO success(long verified) {
        return new RbacDeniedAuditVerificationResultDTO(true, verified, null, null);
    }

    public static RbacDeniedAuditVerificationResultDTO failure(long verified, UUID failingEventId, String reason) {
        return new RbacDeniedAuditVerificationResultDTO(false, verified, failingEventId, reason);
    }
}
