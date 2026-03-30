"use client";

import { useQuery } from "@tanstack/react-query";

import type { SpringPage } from "@/types/api/SpringPage";
import type { InviteRow, InviteStatusFilter } from "@/types/invites/types";
import { fetchInvites } from "@/lib/admin/adminInvites.query";

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
  const queryKey = [
    "invites",
    tenantId,
    page,
    pageSize,
    sort,
    direction,
    email ?? "",
    status ?? "",
  ] as const;

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
    enabled: enabled && Boolean(tenantId),
    placeholderData: (previousData) => previousData,
  });

  return {
    ...query,
    invites: query.data?.content ?? [],
    pageInfo: query.data?.page,
    queryKey, // useful for invalidation if needed
  };
}