import { apiFetch } from "@/lib/apiFetch";
import type {
  AdminTenant,
  Page,
} from "@/types/admin/Tenant";

export function fetchAllTenants(
  page = 0,
  size = 20,
  sort = "createdAt",
  direction: "ASC" | "DESC" = "DESC",
  filters?: {
    status?: string;
    name?: string;
    dataRegion?: string;
    managerName?: string;
    managerEmail?: string;
    createdAfter?: string;
    createdBefore?: string;
  },
) {
  const params = new URLSearchParams();

  params.append("page", String(page));
  params.append("size", String(size));
  params.append("sort", sort);
  params.append("direction", direction);

  if (filters?.status && filters.status !== "ALL")
    params.append("status", filters.status);

  if (filters?.name) params.append("name", filters.name);

  if (filters?.dataRegion) params.append("dataRegion", filters.dataRegion);

  if (filters?.managerName) params.append("managerName", filters.managerName);

  if (filters?.managerEmail) params.append("managerEmail", filters.managerEmail);

  if (filters?.createdAfter) params.append("createdAfter", filters.createdAfter);

  if (filters?.createdBefore) params.append("createdBefore", filters.createdBefore);

  return apiFetch<Page<AdminTenant>>(`/api/admin/tenants?${params.toString()}`);
}

export function fetchManagedTenants() {
  return apiFetch<AdminTenant[]>(`/api/tenants/managed`);
}

export function createTenant(name: string) {
  return apiFetch<AdminTenant>(
    `/api/admin/tenants?name=${encodeURIComponent(name)}`,
    {
      method: "POST",
    },
  );
}

export function suspendTenant(tenantId: string, comment: string) {
  const params = new URLSearchParams();
  params.append("comment", comment);

  return apiFetch<void>(
    `/api/admin/tenants/${tenantId}/suspend?${params.toString()}`,
    {
      method: "POST",
    },
  );
}

export function reactivateTenant(tenantId: string, comment: string) {
  const params = new URLSearchParams();
  params.append("comment", comment);

  return apiFetch<void>(
    `/api/admin/tenants/${tenantId}/reactivate?${params.toString()}`,
    {
      method: "POST",
    },
  );
}

export function terminateTenant(tenantId: string, comment: string) {
  const params = new URLSearchParams();
  params.append("comment", comment);

  return apiFetch<void>(
    `/api/admin/tenants/${tenantId}/terminate?${params.toString()}`,
    {
      method: "POST",
    },
  );
}

export function updateTenant(
  tenantId: string,
  payload: {
    name?: string;
    dataRegion?: string;
    retentionDays?: number;
  },
) {
  const params = new URLSearchParams();

  if (payload.name) params.append("name", payload.name);
  if (payload.dataRegion) params.append("dataRegion", payload.dataRegion);
  if (payload.retentionDays !== undefined)
    params.append("retentionDays", String(payload.retentionDays));

  return apiFetch<void>(`/api/admin/tenants/${tenantId}?${params.toString()}`, {
    method: "PUT",
  });
}
