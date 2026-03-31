"use client";

import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/apiFetch";

import type { SpringPage } from "@/types/api/SpringPage";
import type { TenantUser } from "@/types/admin/TenantUser";

export const tenantUsersKeys = {
  all: ["tenantUsers"] as const,

  lists: () => [...tenantUsersKeys.all, "list"] as const,

  list: (params: {
    tenantId: string;
    page: number;
    size: number;
    sort: string;
    direction: "ASC" | "DESC";
    search?: string;
    jobTitle?: string;
    department?: string;
    role?: string;
    status?: string;
    createdAfter?: string;
    createdBefore?: string;
  }) =>
    [
      ...tenantUsersKeys.lists(),
      params.tenantId,
      params.page,
      params.size,
      params.sort,
      params.direction,
      params.search ?? "",
      params.jobTitle ?? "",
      params.department ?? "",
      params.role ?? "",
      params.status ?? "",
      params.createdAfter ?? "",
      params.createdBefore ?? "",
    ] as const,
};

export function useTenantUsersQuery({
  enabled,
  tenantId,
  page,
  size,
  sort,
  direction,
  search,
  jobTitle,
  department,
  role,
  status,
  createdAfter,
  createdBefore,
}: {
  enabled: boolean;
  tenantId: string;
  page: number;
  size: number;
  sort: string;
  direction: "ASC" | "DESC";
  search?: string;
  jobTitle?: string;
  department?: string;
  role?: string;
  status?: string;
  createdAfter?: string;
  createdBefore?: string;
}) {
  return useQuery<SpringPage<TenantUser>>({
    queryKey: tenantUsersKeys.list({
      tenantId,
      page,
      size,
      sort,
      direction,
      search,
      jobTitle,
      department,
      role,
      status,
      createdAfter,
      createdBefore,
    }),
    queryFn: async () => {
      const params = new URLSearchParams();

      params.append("page", String(page));
      params.append("size", String(size));
      params.append("sort", sort);
      params.append("direction", direction);

      if (search) params.append("search", search);
      if (jobTitle) params.append("jobTitle", jobTitle);
      if (department) params.append("department", department);
      if (role) params.append("role", role);
      if (status) params.append("status", status);
      if (createdAfter) params.append("createdAfter", createdAfter);
      if (createdBefore) params.append("createdBefore", createdBefore);

      return apiFetch<SpringPage<TenantUser>>(
        `/api/admin/tenants/${tenantId}/users?${params.toString()}`,
      );
    },
    enabled,
    placeholderData: (prev) => prev,
  });
}