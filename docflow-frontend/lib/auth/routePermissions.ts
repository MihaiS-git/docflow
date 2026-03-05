export type RealmRole = "ADMIN" | "AUDITOR";

type RoutePermission = {
  prefix: string;
  role: RealmRole;
};

export const ROUTE_PERMISSIONS: RoutePermission[] = [
  { prefix: "/console/audit", role: "AUDITOR" },
  { prefix: "/console/security/audit-keys", role: "AUDITOR" },

  { prefix: "/console/users", role: "ADMIN" },
  { prefix: "/console/tenants", role: "ADMIN" },
  { prefix: "/console/invites", role: "ADMIN" },
];

export function getRequiredRole(pathname: string): RealmRole | null {
  for (const rule of ROUTE_PERMISSIONS) {
    if (pathname.startsWith(rule.prefix)) {
      return rule.role;
    }
  }

  return null;
}