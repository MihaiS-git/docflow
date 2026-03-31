import { apiFetch } from "@/lib/apiFetch";

export async function changeUserStatus(
  tenantId: string,
  userId: string,
  status: string,
  comment: string,
) {
  await apiFetch<void>(
    `/api/admin/tenants/${tenantId}/users/${userId}`,
    {
      method: "PATCH",
      body: JSON.stringify({
        status,
        comment,
      }),
    },
  );
}

export async function changeUserRole(
  tenantId: string,
  userId: string,
  role: string,
  comment: string,
) {
  await apiFetch<void>(
    `/api/admin/tenants/${tenantId}/users/${userId}`,
    {
      method: "PATCH",
      body: JSON.stringify({
        role,
        comment,
      }),
    },
  );
}