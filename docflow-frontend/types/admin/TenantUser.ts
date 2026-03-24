export type TenantUser = {
  userId: string;
  email: string;

  firstName: string;
  lastName: string;
  displayName: string;

  jobTitle?: string | null;
  department?: string | null;

  role: string;
  status: string;

  membershipCreatedAt: string;
  membershipUpdatedAt: string;
};