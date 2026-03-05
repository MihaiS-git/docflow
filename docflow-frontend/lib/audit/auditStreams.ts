export type AuditStream = {
  name: string;
  path: string;
  category: "Security" | "Identity" | "Administration" | "System";
};

export const AUDIT_STREAMS: AuditStream[] = [
{ name: "Authentication", path: "authentication", category: "Security" },
  { name: "RBAC Denied", path: "rbac-denied", category: "Security" },
  { name: "Sensitive Access", path: "sensitive-access", category: "Security" },

  { name: "Credential Lifecycle", path: "credential-lifecycle", category: "Identity" },
  { name: "Identity Projection", path: "identity-projection", category: "Identity" },
  { name: "Onboarding", path: "onboarding", category: "Identity" },

  { name: "Admin", path: "admin", category: "Administration" },

  { name: "Lifecycle Denied", path: "lifecycle-denied", category: "System" },
  { name: "Unauthenticated Access", path: "unauthenticated", category: "System" },
];