export type TenantStatus =
  | "ACTIVE"
  | "SUSPENDED"
  | "TERMINATED";

export interface AdminTenant {
  id: string
  name: string
  description: string
  ownerID: string
  
  status: TenantStatus

  managerName?: string
  managerEmail?: string

  membersCount: number

  dataRegion?: string
  retentionDays?: number

  lastActivity?: string

  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}