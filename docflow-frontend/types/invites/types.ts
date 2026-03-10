export type TenantRole = "MEMBER" | "EXECUTOR" | "REVIEWER" | "MANAGER";

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