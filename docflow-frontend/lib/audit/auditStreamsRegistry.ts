export const AUDIT_STREAMS = {
  authentication: {
    key: "authentication",
    title: "Authentication Audit",
    endpoint: "/api/audit/authentication",
    exportBase: "authentication-audit",
    path: "/console/audit/streams/authentication",
  },

  credentialLifecycle: {
    key: "credentialLifecycle",
    title: "Credential Lifecycle Audit",
    endpoint: "/api/audit/credential-lifecycle",
    exportBase: "credential-lifecycle-audit",
    path: "/console/audit/streams/credential-lifecycle",
  },

  lifecycleDenied: {
    key: "lifecycleDenied",
    title: "Lifecycle Denied Audit",
    endpoint: "/api/audit/lifecycle-denied",
    exportBase: "lifecycle-denied-audit",
    path: "/console/audit/streams/lifecycle-denied",
  },

  rbacDenied: {
    key: "rbacDenied",
    title: "RBAC Denied Audit",
    endpoint: "/api/audit/rbac-denied",
    exportBase: "rbac-denied-audit",
    path: "/console/audit/streams/rbac-denied",
  },

  sensitiveAccess: {
    key: "sensitiveAccess",
    title: "Sensitive Access Audit",
    endpoint: "/api/audit/sensitive-access",
    exportBase: "sensitive-access-audit",
    path: "/console/audit/streams/sensitive-access",
  },

  admin: {
    key: "admin",
    title: "Admin Actions Audit",
    endpoint: "/api/audit/admin-actions",
    exportBase: "admin-actions-audit",
    path: "/console/audit/streams/admin",
  },

  onboarding: {
    key: "onboarding",
    title: "Onboarding Audit",
    endpoint: "/api/audit/onboarding",
    exportBase: "onboarding-audit",
    path: "/console/audit/streams/onboarding",
  },

  unauthenticated: {
    key: "unauthenticated",
    title: "Unauthenticated Access Audit",
    endpoint: "/api/audit/unauthenticated-access",
    exportBase: "unauthenticated-access-audit",
    path: "/console/audit/streams/unauthenticated",
  },

  identityProjection: {
    key: "identityProjection",
    title: "Identity Projection Audit",
    endpoint: "/api/audit/identity-projection",
    exportBase: "identity-projection-audit",
    path: "/console/audit/identity-projection",
  },

  exports: {
    key: "exports",
    title: "Audit Export Snapshots",
    path: "/console/audit/exports",
  },

  retention: {
    key: "retention",
    title: "Retention Policies",
    path: "/console/audit/retention",
  },

  auditKeys: {
    key: "auditKeys",
    title: "Export Signing Keys",
    path: "/console/security/audit-keys",
  },
} as const;

export type AuditStreamKey = keyof typeof AUDIT_STREAMS;