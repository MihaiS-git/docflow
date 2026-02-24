package com.brutecx.docflow_backend.domain.audit.retention;

import com.brutecx.docflow_backend.audit.admin.AdminAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.auth.AuthenticationAuditCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.identity.IdentityProjectionCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.onboarding.OnboardingCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.rbac.RbacDeniedCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessCanonicalMaterialBuilder;
import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessCanonicalMaterialBuilder;

import java.util.List;

/**
 * Centralizes stream identifiers to avoid drift.
 * The stream string must match what the UI/controllers use in exports.
 */
public final class AuditRetentionTargets {

    private AuditRetentionTargets() {
    }

    public static final List<String> STREAMS = List.of(
            AdminAuditCanonicalMaterialBuilder.STREAM,
            AuthenticationAuditCanonicalMaterialBuilder.STREAM,
            CredentialLifecycleCanonicalMaterialBuilder.STREAM,
            IdentityProjectionCanonicalMaterialBuilder.STREAM,
            OnboardingCanonicalMaterialBuilder.STREAM,
            RbacDeniedCanonicalMaterialBuilder.STREAM,
            SensitiveAccessCanonicalMaterialBuilder.STREAM,
            UnauthenticatedAccessCanonicalMaterialBuilder.STREAM
    );
}