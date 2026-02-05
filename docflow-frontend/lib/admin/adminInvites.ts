import { apiFetch } from "@/lib/apiFetch";
import type { AdminInvite, Page } from "@/types/admin/Invite";

export function fetchAdminInvites(page = 0, size = 20) {
  return apiFetch<Page<AdminInvite>>(
    `/api/admin/invites?page=${page}&size=${size}`
  );
}

export function revokeInvite(inviteId: string) {
  return apiFetch<void>(`/api/admin/invites/${inviteId}/revoke`, {
    method: "POST",
  });
}

export function cleanupInvites() {
  return apiFetch<{ deletedInvites: number; deletedUsers: number }>(
    `/api/admin/invites/cleanup`,
    { method: "POST" }
  );
}
