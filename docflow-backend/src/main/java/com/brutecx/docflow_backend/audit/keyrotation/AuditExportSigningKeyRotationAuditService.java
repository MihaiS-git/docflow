package com.brutecx.docflow_backend.audit.keyrotation;

public interface AuditExportSigningKeyRotationAuditService {
    void recordRotation(
            AuditExportSigningKeyRotationReason reason,
            String oldKeyId,
            String newKeyId,
            String newFingerprintSha256Hex
    );
}