import { apiFetch } from "@/lib/apiFetch";
import type { SpringPage } from "@/types/api/SpringPage";
import type { InviteRow, InviteStatusFilter } from "@/types/invites/types";

export type FetchInvitesParams = {
  tenantId: string;
  page: number;
  size: number;
  sort: string;
  direction: "ASC" | "DESC";
  email?: string;
  status?: InviteStatusFilter;
};

export async function fetchInvites({
  tenantId,
  page,
  size,
  sort,
  direction,
  email,
  status,
}: FetchInvitesParams): Promise<SpringPage<InviteRow>> {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size),
    sort,
    direction,
  });

  if (email) query.set("email", email);
  if (status) query.set("status", status);

  return apiFetch<SpringPage<InviteRow>>(
    `/api/tenants/${tenantId}/invites?${query.toString()}`
  );
}