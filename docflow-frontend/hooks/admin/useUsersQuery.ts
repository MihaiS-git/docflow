import { useQuery } from "@tanstack/react-query";

import { fetchAdminUsers } from "@/lib/admin/adminUsers";
import { usersKeys } from "@/lib/queryKeys/usersKeys";

import type { SpringPage } from "@/types/api/SpringPage";
import type { AdminUser } from "@/types/admin/AdminUser";

type Params = {
  enabled: boolean;
  page: number;
  size: number;
  sort: string;
  direction: "ASC" | "DESC";
  tenantId?: string;
  status?: string;
  email?: string;
};

export function useUsersQuery({
  enabled,
  page,
  size,
  sort,
  direction,
  tenantId,
  status,
  email,
}: Params) {
  return useQuery<SpringPage<AdminUser>>({
    queryKey: usersKeys.list({
      page,
      size,
      sort,
      direction,
      tenantId,
      status,
      email,
    }),
    queryFn: () =>
      fetchAdminUsers(page, size, sort, direction, {
        tenantId,
        status,
        email,
      }),
    enabled,
    placeholderData: (prev) => prev,
  });
}