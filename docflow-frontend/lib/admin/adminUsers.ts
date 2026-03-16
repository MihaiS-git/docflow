import { apiFetch } from "@/lib/apiFetch";
import type { AdminUser } from "@/types/admin/AdminUser";
import type { SpringPage } from "@/types/api/SpringPage";

export function activateUser(userId: string) {
  return apiFetch<void>(`/api/admin/users/${userId}/activate`, {
    method: "POST",
  });
}

export function lockUser(userId: string) {
  return apiFetch<void>(`/api/admin/users/${userId}/lock`, {
    method: "POST",
  });
}

export function disableUser(userId: string) {
  return apiFetch<void>(`/api/admin/users/${userId}/disable`, {
    method: "POST",
  });
}

export function assignRole(userId: string, roleName: string) {
  return apiFetch<void>(
    `/api/admin/users/${userId}/assign-role?roleName=${encodeURIComponent(roleName)}`,
    { method: "POST" },
  );
}

export function revokeRole(userId: string, roleName: string) {
  return apiFetch<void>(
    `/api/admin/users/${userId}/revoke-role?roleName=${encodeURIComponent(roleName)}`,
    { method: "POST" },
  );
}

type FetchUsersFilters = {
  tenantId?: string;
  status?: string;
  email?: string;
};

export function fetchAdminUsers(
  page = 0,
  size = 20,
  sort = "email",
  direction: "ASC" | "DESC" = "ASC",
  filters?: FetchUsersFilters,
): Promise<SpringPage<AdminUser>> {
  const params = new URLSearchParams();

  params.append("page", String(page));
  params.append("size", String(size));
  params.append("sort", sort);
  params.append("direction", direction);

  if (filters?.tenantId) params.append("tenantId", filters.tenantId);
  if (filters?.status) params.append("status", filters.status);
  if (filters?.email) params.append("email", filters.email);

  return apiFetch(`/api/admin/users?${params.toString()}`);
}