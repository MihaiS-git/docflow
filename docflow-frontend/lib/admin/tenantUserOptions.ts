import type { MembershipStatus, TenantRole } from "@/types/admin/TenantUser";

export const ALL_ROLES_OPTION = {
  value: "" as const,
  label: "All roles",
};

export const ROLE_LABELS: Record<TenantRole, string> = {
  MANAGER: "Manager",
  MEMBER: "Member",
  EXECUTOR: "Executor",
  REVIEWER: "Reviewer",
};

export const ROLE_OPTIONS_FILTER: {
  value: TenantRole | "";
  label: string;
}[] = [
  ALL_ROLES_OPTION,
  ...(Object.keys(ROLE_LABELS) as TenantRole[]).map((value) => ({
    value,
    label: ROLE_LABELS[value],
  })),
];

export const ROLE_OPTIONS_MUTATION: {
  value: TenantRole;
  label: string;
}[] = (Object.keys(ROLE_LABELS) as TenantRole[]).map((value) => ({
  value,
  label: ROLE_LABELS[value],
}));

export const ALL_MEMBERSHIP_STATUSES = {
  value: "" as const,
  label: "All statuses",
};

export const MEMBERSHIP_STATUS_LABELS: Record<MembershipStatus, string> = {
  ACTIVE: "Active",
  SUSPENDED: "Suspended",
};

export const MEMBERSHIP_STATUS_OPTIONS: {
  value: MembershipStatus | "";
  label: string;
}[] = [
  ALL_MEMBERSHIP_STATUSES,
  ...(Object.keys(MEMBERSHIP_STATUS_LABELS) as MembershipStatus[]).map(
    (value) => ({
      value,
      label: MEMBERSHIP_STATUS_LABELS[value],
    }),
  ),
];
