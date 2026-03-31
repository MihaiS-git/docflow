export type TenantRole =
  | "MANAGER"
  | "MEMBER"
  | "EXECUTOR"
  | "REVIEWER";

export type MembershipStatus = "ACTIVE" | "SUSPENDED";

export type TenantUser = {
  userId: string;
  email: string;
  businessPhone?: string;

  firstName: string;
  lastName: string;
  displayName: string;

  jobTitle?: string | null;
  department?: string | null;

  role: TenantRole;
  status: MembershipStatus;

  createdAt: string;
  updatedAt: string;
};
