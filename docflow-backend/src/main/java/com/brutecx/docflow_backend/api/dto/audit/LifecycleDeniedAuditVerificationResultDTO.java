package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record LifecycleDeniedAuditVerificationResultDTO(
        boolean success,
        long verified,
        UUID failingEventId,
        String reason
) {
    public static LifecycleDeniedAuditVerificationResultDTO success(long verified) {
        return new LifecycleDeniedAuditVerificationResultDTO(true, verified, null, null);
    }

    public static LifecycleDeniedAuditVerificationResultDTO failure(long verified, UUID failingEventId, String reason) {
        return new LifecycleDeniedAuditVerificationResultDTO(false, verified, failingEventId, reason);
    }
}
