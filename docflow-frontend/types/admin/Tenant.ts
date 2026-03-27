export type TenantStatus = "ACTIVE" | "SUSPENDED" | "TERMINATED";

export interface AdminTenant {
  id: string;
  name: string;
  description: string;
  ownerId: string;

  status: TenantStatus;

  managerName?: string;
  managerEmail?: string;

  membersCount: number;

  dataRegion?: string;
  retentionDays?: number;

  lastActivity?: string;

  createdAt: string;
  updatedAt: string;
}

export interface TenantResponseDTO {
  id: string;
  name: string;
  description: string;
  ownerId: string;
  ownerDisplayName: string;
  status: TenantStatus;
  tenantType: string;
  dataRegion: string;
  retentionDays: number;
  bootstrapEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}
