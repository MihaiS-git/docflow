export type TenantStatus = "ACTIVE" | "SUSPENDED";

export interface AdminTenant {
  id: string;
  name: string;
  status: string;
  dataRegion: string | null;
  retentionDays: number | null;
  bootstrapEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
