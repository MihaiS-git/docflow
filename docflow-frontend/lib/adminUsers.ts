import { apiFetch } from "@/lib/apiFetch";
import type { AdminUser } from "@/types/admin/AdminUser";

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

export function fetchAdminUsers(): Promise<AdminUser[]> {
  return apiFetch<AdminUser[]>("/api/admin/users");
}
