export type TenantUser = {
  userId: string;
  email: string;
  businessPhone?: string;

  firstName: string;
  lastName: string;
  displayName: string;

  jobTitle?: string | null;
  department?: string | null;

  role: string;
  status: string;

  createdAt: string;
  updatedAt: string;
};

