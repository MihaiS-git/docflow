"use client";

import { useQuery } from "@tanstack/react-query";

import type { SpringPage } from "@/types/api/SpringPage";
import type { AdminTenant } from "@/types/admin/Tenant";
import { fetchAllTenants } from "@/lib/admin/adminTenants";

type Params = {
  page: number;
  pageSize: number;
  sort: string;
  direction: "ASC" | "DESC";
  status?: string;
  name?: string;
  dataRegion?: string;
  managerName?: string;
  managerEmail?: string;
  createdAfter?: string;
  createdBefore?: string;
  enabled?: boolean;
};

export function useTenantsQuery({
  page,
  pageSize,
  sort,
  direction,
  status,
  name,
  dataRegion,
  managerName,
  managerEmail,
  createdAfter,
  createdBefore,
  enabled = true,
}: Params) {
  const queryKey = [
    "tenants",
    page,
    pageSize,
    sort,
    direction,
    status ?? "",
    name ?? "",
    dataRegion ?? "",
    managerName ?? "",
    managerEmail ?? "",
    createdAfter ?? "",
    createdBefore ?? "",
  ] as const;

  const query = useQuery<SpringPage<AdminTenant>>({
    queryKey,
    queryFn: () =>
      fetchAllTenants(page, pageSize, sort, direction, {
        status,
        name,
        dataRegion,
        managerName,
        managerEmail,
        createdAfter,
        createdBefore,
      }),
    enabled,
    placeholderData: (previousData) => previousData,
  });

  return {
    ...query,
    tenants: query.data?.content ?? [],
    pageInfo: query.data?.page,
    queryKey,
  };
}