export const TENANT_ROLES = [
  "MEMBER",
  "EXECUTOR",
  "REVIEWER",
  "MANAGER",
] as const;
export type TenantRole = (typeof TENANT_ROLES)[number];

export type InviteStatus = "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";
export type InviteStatusFilter = "" | InviteStatus;

export type InviteRow = {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  department: string | null;
  tenantRole: TenantRole;
  status: InviteStatus;
  createdAt: string;
  expiresAt: string;
};

export type InvitePage = {
  content: InviteRow[];
  number: number;
  totalPages: number;
  totalElements: number;
  first: boolean;
  last: boolean;
};