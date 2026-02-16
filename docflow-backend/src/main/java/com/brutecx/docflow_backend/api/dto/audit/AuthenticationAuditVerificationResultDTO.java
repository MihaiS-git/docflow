package com.brutecx.docflow_backend.api.dto.audit;

import java.util.UUID;

public record AuthenticationAuditVerificationResultDTO(
        boolean ok,
        long verified,
        UUID failedAtEventId,
        String message
) {
    public static AuthenticationAuditVerificationResultDTO success(long verified) {
        return new AuthenticationAuditVerificationResultDTO(true, verified, null, "OK");
    }

    public static AuthenticationAuditVerificationResultDTO failure(long verified, UUID failedAtEventId, String message) {
        return new AuthenticationAuditVerificationResultDTO(false, verified, failedAtEventId, message);
    }
}
