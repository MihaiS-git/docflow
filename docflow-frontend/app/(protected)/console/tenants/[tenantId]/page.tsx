"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import Card from "@/components/ui/Card";
import DataTable from "@/components/ui/DataTable";

import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

import type { AdminTenant } from "@/types/admin/Tenant";
import type { SpringPage } from "@/types/api/SpringPage";
import type { TenantUser } from "@/types/admin/TenantUser";

export default function TenantDetailsPage() {
  const { tenantId } = useParams<{ tenantId: string }>();

  const [tenant, setTenant] = useState<AdminTenant | null>(null);
  const [usersPage, setUsersPage] = useState<SpringPage<TenantUser> | null>(
    null,
  );

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!tenantId) return;

    let cancelled = false;

    async function load() {
      setLoading(true);
      setError(null);

      try {
        const [tenantRes, usersRes] = await Promise.all([
          apiFetch<AdminTenant>(`/api/admin/tenants/${tenantId}`),
          apiFetch<SpringPage<TenantUser>>(
            `/api/admin/tenants/${tenantId}/users?page=0&size=20`,
          ),
        ]);

        if (!cancelled) {
          setTenant(tenantRes);
          setUsersPage(usersRes);
        }
      } catch (err) {
        if (cancelled) return;

        if (err instanceof ApiError) setError(err.message);
        else if (err instanceof Error) setError(err.message);
        else setError("Unexpected error");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [tenantId]);

  return (
    <PageContainer>
      <PageHeader
        title="Tenant details"
        description="Inspect tenant configuration and users."
      />

      {loading && <p className="text-sm">Loading…</p>}

      {error && (
        <p className="text-sm text-(--color-error)">
          {error}
        </p>
      )}

      {tenant && (
        <div className="flex flex-col gap-6">
          {/* Overview */}
          <Card title="Overview">
            <div className="grid gap-2 text-sm">
              <p>
                <strong>Name:</strong> {tenant.name}
              </p>
              <p>
                <strong>Status:</strong> {tenant.status}
              </p>
              <p>
                <strong>Region:</strong> {tenant.dataRegion}
              </p>
              <p>
                <strong>Retention:</strong> {tenant.retentionDays}
              </p>
              <p>
                <strong>Description:</strong>{" "}
                {tenant.description || "-"}
              </p>
            </div>
          </Card>

          {/* Users */}
          <Card title="Users">
            {!usersPage || usersPage.content.length === 0 ? (
              <p className="text-sm">No users</p>
            ) : (
              <DataTable empty={false}>
                <thead>
                  <tr>
                    <th className="px-4 py-2 text-left text-xs font-medium">
                      User
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium">
                      Role
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium">
                      Status
                    </th>
                    <th className="px-4 py-2 text-left text-xs font-medium">
                      Department
                    </th>
                  </tr>
                </thead>

                <tbody>
                  {usersPage.content.map((u) => (
                    <tr key={u.userId} className="border-t">
                      {/* User */}
                      <td className="px-4 py-2 text-sm">
                        <div className="flex flex-col">
                          <span>{u.displayName}</span>
                          <span className="text-xs text-(--color-text-muted)">
                            {u.email}
                          </span>
                          {(u.jobTitle || u.department) && (
                            <span className="text-xs text-(--color-text-muted)">
                              {[u.jobTitle, u.department]
                                .filter(Boolean)
                                .join(" • ")}
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Role */}
                      <td className="px-4 py-2 text-sm">
                        {u.role}
                      </td>

                      {/* Status */}
                      <td className="px-4 py-2 text-sm">
                        {u.status}
                      </td>

                      {/* Department */}
                      <td className="px-4 py-2 text-sm">
                        {u.department || "-"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </DataTable>
            )}
          </Card>
        </div>
      )}
    </PageContainer>
  );
}