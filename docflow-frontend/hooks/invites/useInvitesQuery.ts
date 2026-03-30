"use client";

import { useQuery } from "@tanstack/react-query";

import type { SpringPage } from "@/types/api/SpringPage";
import type { InviteRow, InviteStatusFilter } from "@/types/invites/types";
import { fetchInvites } from "@/lib/admin/adminInvites.query";
import { invitesKeys } from "@/lib/queryKeys/invitesKeys";

type Params = {
  tenantId: string;
  page: number;
  pageSize: number;
  sort: string;
  direction: "ASC" | "DESC";
  email?: string;
  status?: InviteStatusFilter;
  enabled?: boolean;
};

export function useInvitesQuery({
  tenantId,
  page,
  pageSize,
  sort,
  direction,
  email,
  status,
  enabled = true,
}: Params) {
  const queryKey = invitesKeys.list(tenantId, {
    page,
    pageSize,
    sort,
    direction,
    email,
    status,
  });

  const query = useQuery<SpringPage<InviteRow>>({
    queryKey,
    queryFn: () =>
      fetchInvites({
        tenantId,
        page,
        size: pageSize,
        sort,
        direction,
        email: email || undefined,
        status: status || undefined,
      }),
    enabled: enabled,
    placeholderData: (prev) => prev,
  });

  return {
    ...query,
    invites: query.data?.content ?? [],
    pageInfo: query.data?.page,
    queryKey, // useful for invalidation if needed
  };
}
