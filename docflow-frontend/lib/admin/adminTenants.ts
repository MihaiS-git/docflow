import { apiFetch } from "@/lib/apiFetch";
import type { AdminTenant } from "@/types/admin/Tenant";
import { SpringPage } from "@/types/api/SpringPage";

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

  if (filters?.status && filters.status !== "ALL") {
    params.append("status", filters.status);
  }

  if (filters?.name) {
    params.append("name", filters.name);
  }

  if (filters?.dataRegion) {
    params.append("dataRegion", filters.dataRegion);
  }

  if (filters?.managerName) {
    params.append("managerName", filters.managerName);
  }

  if (filters?.managerEmail) {
    params.append("managerEmail", filters.managerEmail);
  }

  if (filters?.createdAfter) {
    params.append("createdAfter", filters.createdAfter);
  }

  if (filters?.createdBefore) {
    params.append("createdBefore", filters.createdBefore);
  }

  return apiFetch<SpringPage<AdminTenant>>(
    `/api/admin/tenants?${params.toString()}`,
  );
}

export function fetchManagedTenants() {
  return apiFetch<AdminTenant[]>(`/api/tenants/managed`);
}

export function createTenant(
  name: string,
  description?: string,
  dataRegion?: string,
  retentionDays?: number,
) {
  return apiFetch<AdminTenant>(`/api/admin/tenants`, {
    method: "POST",
    body: JSON.stringify({
      name,
      description: description || undefined,
      dataRegion: dataRegion || undefined,
      retentionDays: retentionDays ?? undefined,
    }),
    headers: {
      "Content-Type": "application/json",
    },
  });
}

export function suspendTenant(tenantId: string, comment: string) {
  return apiFetch<void>(`/api/admin/tenants/${tenantId}/suspend`, {
    method: "POST",
    body: JSON.stringify({ comment }),
    headers: {
      "Content-Type": "application/json",
    },
  });
}

export function reactivateTenant(tenantId: string, comment: string) {
  return apiFetch<void>(`/api/admin/tenants/${tenantId}/reactivate`, {
    method: "POST",
    body: JSON.stringify({ comment }),
    headers: {
      "Content-Type": "application/json",
    },
  });
}

export function terminateTenant(tenantId: string, comment: string) {
  return apiFetch<void>(`/api/admin/tenants/${tenantId}/terminate`, {
    method: "POST",
    body: JSON.stringify({ comment }),
    headers: {
      "Content-Type": "application/json",
    },
  });
}

export function updateTenant(
  tenantId: string,
  payload: {
    name?: string;
    description?: string;
    dataRegion?: string;
    retentionDays?: number;
    comment?: string;
  },
) {
  return apiFetch<void>(`/api/admin/tenants/${tenantId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
    headers: {
      "Content-Type": "application/json",
    },
  });
}