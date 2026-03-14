"use client";

import { useEffect, useState } from "react";
import type { AdminUser } from "@/types/admin/AdminUser";
import type { SpringPage } from "@/types/api/SpringPage";
import type { AdminTenantLookup } from "@/types/admin/Tenant";

import { fetchAdminUsers } from "@/lib/admin/adminUsers";
import { fetchAdminRoles } from "@/lib/admin/adminRoles";
import { fetchTenantsLookup } from "@/lib/admin/adminTenants";

import { AdminUserRow } from "./AdminUserRow";
import { useAuth } from "@/lib/auth/useAuth";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

const PAGE_SIZE = 20;

export default function AdminUsersPage() {
  const { status, identity } = useAuth();

  const authLoading = status === "LOADING";
  const isAdmin = identity?.roles.includes("ADMIN");

  const [page, setPage] = useState<SpringPage<AdminUser> | null>(null);

  const [roles, setRoles] = useState<string[]>([]);
  const [tenants, setTenants] = useState<AdminTenantLookup[]>([]);

  const [pageIndex, setPageIndex] = useState(0);

  const [tenantFilter, setTenantFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [emailFilter, setEmailFilter] = useState("");

  useEffect(() => {
    if (status !== "AUTH" || !isAdmin) return;

    fetchTenantsLookup().then((res) => setTenants(res ?? []));
  }, [status, isAdmin]);

  useEffect(() => {
    if (status !== "AUTH" || !isAdmin) return;

    Promise.all([
      fetchAdminUsers({
        page: pageIndex,
        size: PAGE_SIZE,
        tenantId: tenantFilter || undefined,
        status: statusFilter || undefined,
        email: emailFilter || undefined,
      }),
      fetchAdminRoles(),
    ]).then(([pageData, rolesData]) => {
      setPage(pageData);
      setRoles(rolesData ?? []);
    });
  }, [status, isAdmin, pageIndex, tenantFilter, statusFilter, emailFilter]);

  if (authLoading)
    return (
      <PageContainer>
        <p className="text-sm text-(--color-text-secondary)">Loading auth…</p>
      </PageContainer>
    );

  if (status !== "AUTH")
    return (
      <PageContainer>
        <p className="text-sm text-(--color-text-secondary)">Please login.</p>
      </PageContainer>
    );

  if (!isAdmin)
    return (
      <PageContainer>
        <p className="text-sm text-(--color-error)">Access denied</p>
      </PageContainer>
    );

  if (!page)
    return (
      <PageContainer>
        <p className="text-sm text-(--color-text-secondary)">
          Loading admin data…
        </p>
      </PageContainer>
    );

  const updateUser = (updated: AdminUser) => {
    setPage((prev) =>
      prev
        ? {
            ...prev,
            content: (prev.content ?? []).map((u) =>
              u.id === updated.id ? updated : u,
            ),
          }
        : prev,
    );
  };

  const canPrev = pageIndex > 0;
  const canNext = pageIndex < page.totalPages - 1;

  return (
    <PageContainer>
      <PageHeader
        title="Admin — Users"
        description="Manage user accounts, roles, and account status."
      />

      <Card>
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="grid gap-3 sm:grid-cols-3 w-full">
            <Select
              value={tenantFilter}
              onChange={(e) => {
                setPageIndex(0);
                setTenantFilter(e.target.value);
              }}
            >
              <option value="">All tenants</option>
              {(tenants ?? []).map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </Select>

            <Select
              value={statusFilter}
              onChange={(e) => {
                setPageIndex(0);
                setStatusFilter(e.target.value);
              }}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="LOCKED">LOCKED</option>
              <option value="DISABLED">DISABLED</option>
            </Select>

            <Input
              type="search"
              placeholder="Search email…"
              value={emailFilter}
              onChange={(e) => {
                setPageIndex(0);
                setEmailFilter(e.target.value);
              }}
            />
          </div>
        </div>
      </Card>

      <Card>
        <div className="overflow-x-auto overflow-y-visible">
          <div className="rounded-lg border border-(--color-border) bg-(--color-surface)">
            <table className="min-w-full table-fixed">
              <thead className="bg-(--color-table-header) text-(--color-text-secondary)">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide">
                    Email
                  </th>

                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide">
                    Status
                  </th>

                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide">
                    Roles
                  </th>

                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide">
                    Actions
                  </th>
                </tr>
              </thead>

              <tbody className="bg-(--color-table-row)">
                {(page.content ?? []).map((u) => (
                  <AdminUserRow
                    key={u.id}
                    user={u}
                    allRoles={roles}
                    onUserUpdated={updateUser}
                  />
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="mt-4 flex flex-col gap-3 text-sm text-(--color-text-secondary) sm:flex-row sm:items-center sm:justify-between">
          <span>
            Page {pageIndex + 1} / {Math.max(page.totalPages, 1)}
          </span>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!canPrev}
              onClick={() => setPageIndex((p) => p - 1)}
            >
              Prev
            </Button>

            <Button
              variant="outline"
              size="sm"
              disabled={!canNext}
              onClick={() => setPageIndex((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      </Card>
    </PageContainer>
  );
}
