import { useQuery } from "@tanstack/react-query";
import { fetchManagedTenants } from "@/lib/admin/adminTenants";
import type { AdminTenant } from "@/types/admin/Tenant";

export function useManagedTenantsQuery(enabled: boolean) {
  return useQuery<AdminTenant[]>({
    queryKey: ["managed-tenants"],
    queryFn: fetchManagedTenants,
    enabled,
  });
}