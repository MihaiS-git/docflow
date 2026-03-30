export const TENANT_ROLES = [
  "MEMBER",
  "EXECUTOR",
  "REVIEWER",
  "MANAGER",
] as const;
export type TenantRole = (typeof TENANT_ROLES)[number];

export type InviteStatus = "PENDING" | "ACTIVATED" | "ACCEPTED" | "REVOKED" | "EXPIRED";
export type InviteStatusFilter = "" | InviteStatus;

export const ALL_STATUSES_OPTION = { value: "", label: "All statuses" };
export const STATUS_LABELS: Record<InviteStatus, string> = {
  PENDING: "Pending",
  ACTIVATED: "Activated",
  ACCEPTED: "Accepted",
  REVOKED: "Revoked",
  EXPIRED: "Expired",
};

export const STATUS_OPTIONS = [
  ALL_STATUSES_OPTION,
  ...Object.entries(STATUS_LABELS).map(([value, label]) => ({
    value,
    label,
  })),
];

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