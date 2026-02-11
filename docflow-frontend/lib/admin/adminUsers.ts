import { apiFetch } from "@/lib/apiFetch";
import type { AdminUser } from "@/types/admin/AdminUser";
import { SpringPage } from "@/types/api/SpringPage";

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

type FetchUsersParams = {
  page?: number;
  size?: number;
  status?: string;
  email?: string;
  tenantId?: string;
};

export function fetchAdminUsers(
  params: FetchUsersParams = {},
): Promise<SpringPage<AdminUser>> {
  const qs = new URLSearchParams();

  if (params.page !== undefined) qs.set("page", String(params.page));

  if (params.size !== undefined) qs.set("size", String(params.size));

  if (params.tenantId) qs.set("tenantId", params.tenantId);

  if (params.status) qs.set("status", params.status);

  if (params.email) qs.set("email", params.email);

  const url = qs.toString() ? `/api/admin/users?${qs}` : `/api/admin/users`;

  return apiFetch(url);
}
