export type InviteStatus = "PENDING" | "ACCEPTED";

export interface AdminInvite {
  id: string;
  email: string;
  status: InviteStatus;
  createdAt: string;
  expiresAt: string;
  ageSeconds: number;
  tenantId: string;
  tenantName: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
