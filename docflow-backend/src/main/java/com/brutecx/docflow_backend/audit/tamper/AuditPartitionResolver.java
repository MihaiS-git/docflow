package com.brutecx.docflow_backend.audit.tamper;

import org.springframework.stereotype.Component;

@Component
public class AuditPartitionResolver {

    public AuditPartition authentication(String subjectId) {
        return AuditPartition.subject("AUTHENTICATION", subjectId);
    }

    public AuditPartition credentialLifecycle(String subjectExternalId) {
        return AuditPartition.subject("CREDENTIAL_LIFECYCLE", subjectExternalId);
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
}
