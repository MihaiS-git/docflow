package com.brutecx.docflow_backend.audit.tamper;

import com.brutecx.docflow_backend.audit.admin.AdminAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.auth.AuthenticationAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.identity.IdentityProjectionCanonicalMaterialBuilder;
import org.springframework.stereotype.Component;

@Component
public class AuditPartitionResolver {

    public AuditPartition admin(String tenantId) {
        return AuditPartition.tenant(AdminAuditCanonicalMaterialBuilder.STREAM, tenantId);
    }

    public AuditPartition authentication(String subjectId) {
        return AuditPartition.subject(AuthenticationAuditCanonicalMaterialBuilder.STREAM, subjectId);
    }

    public AuditPartition credentialLifecycle(String subjectExternalId) {
        return AuditPartition.subject(CredentialLifecycleCanonicalMaterialBuilder.STREAM, subjectExternalId);
    }

    public AuditPartition identityProjection(String subjectId) {
        return AuditPartition.subject(
                IdentityProjectionCanonicalMaterialBuilder.STREAM,
                subjectId
        );
    }

    public AuditPartition lifecycleDenied(String subjectId) {
        return AuditPartition.subject("LIFECYCLE_DENIED", subjectId);
    }

    public AuditPartition rbacDenied(String subjectId) {
        return AuditPartition.subject("RBAC_DENIED", subjectId);
    }

    public AuditPartition onboarding(String tenantId) {
        return AuditPartition.tenant("ONBOARDING", tenantId);
    }

    public AuditPartition sensitiveAccess(String tenantId) {
        return AuditPartition.tenant("SENSITIVE_ACCESS", tenantId);
    }

    public AuditPartition unauthenticatedAccess() {
        return AuditPartition.global("UNAUTHENTICATED_ACCESS");
    }

//    public AuditPartition auditExportSigningKeyRotation() {
//        return AuditPartition.global("AUDIT_EXPORT_SIGNING_KEY_ROTATION");
//    }
}