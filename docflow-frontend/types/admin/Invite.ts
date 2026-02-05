export type InviteStatus = "PENDING" | "ACCEPTED";

export interface AdminInvite {
  id: string;
  email: string;
  status: InviteStatus;
  createdAt: string;   // ISO
  expiresAt: string;   // ISO
  ageSeconds: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
