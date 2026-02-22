package com.brutecx.docflow_backend.audit.keyrotation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuditExportSigningKeyRotationMetadata(
        @JsonProperty("reason") AuditExportSigningKeyRotationReason reason,
        @JsonProperty("oldKeyId") String oldKeyId,
        @JsonProperty("newKeyId") String newKeyId,
        @JsonProperty("newFingerprintSha256Hex") String newFingerprintSha256Hex
) {
}