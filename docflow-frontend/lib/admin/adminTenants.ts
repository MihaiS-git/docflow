import { apiFetch } from "@/lib/apiFetch";
import type { AdminTenant, Page } from "@/types/admin/Tenant";

export function fetchActiveTenants(page = 0, size = 100) {
  return apiFetch<Page<AdminTenant>>(
    `/api/admin/tenants/active?page=${page}&size=${size}`
  );
}

export function fetchAllTenants(page = 0, size = 50) {
  return apiFetch<Page<AdminTenant>>(
    `/api/admin/tenants?page=${page}&size=${size}`
  );
}

export function fetchManagedTenants() {
  return apiFetch<AdminTenant[]>(`/api/tenants/managed`);
}

export function createTenant(name: string) {
  return apiFetch<AdminTenant>(
    `/api/admin/tenants?name=${encodeURIComponent(name)}`,
    {
      method: "POST",
    }
  );
}