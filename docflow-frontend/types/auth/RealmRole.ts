export const REALM_ROLES = {
  ADMIN: "ADMIN",
  USER: "USER",
  AUDITOR: "AUDITOR",
} as const;

export type RealmRole =
  (typeof REALM_ROLES)[keyof typeof REALM_ROLES];