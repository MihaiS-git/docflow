"use client";

import { useEffect, useState } from "react";
import type { AdminUser } from "@/types/admin/AdminUser";
import type { SpringPage } from "@/types/api/SpringPage";
import type { AdminTenant } from "@/types/admin/Tenant";

import { fetchAdminUsers } from "@/lib/admin/adminUsers";
import { fetchAdminRoles } from "@/lib/admin/adminRoles";
import { fetchActiveTenants } from "@/lib/admin/adminTenants";

import { AdminUserRow } from "../AdminUserRow";
import { useAuth } from "@/lib/auth/useAuth";

const PAGE_SIZE = 20;

export default function AdminUsersPage() {
  const { status, identity } = useAuth();

  const authLoading = status === "LOADING";
  const isAdmin = identity?.roles.includes("ADMIN");

  const [page, setPage] =
    useState<SpringPage<AdminUser> | null>(null);

  const [roles, setRoles] = useState<string[]>([]);
  const [tenants, setTenants] = useState<AdminTenant[]>([]);

  const [pageIndex, setPageIndex] = useState(0);

  const [tenantFilter, setTenantFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [emailFilter, setEmailFilter] = useState("");

  // --- load tenants once ---

  useEffect(() => {
    if (status !== "AUTH" || !isAdmin) return;

    fetchActiveTenants().then(res =>
      setTenants(res.content)
    );
  }, [status, isAdmin]);

  // --- load users ---

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
      setRoles(rolesData);
    });
  }, [
    status,
    isAdmin,
    pageIndex,
    tenantFilter,
    statusFilter,
    emailFilter,
  ]);

  // --- guards ---

  if (authLoading) return <div>Loading auth…</div>;

  if (status !== "AUTH") return <div>Please login.</div>;

  if (!isAdmin)
    return <div className="text-red-600">Access denied</div>;

  if (!page) return <div>Loading admin data…</div>;

  // --- update helper ---

  const updateUser = (updated: AdminUser) => {
    setPage(prev =>
      prev
        ? {
            ...prev,
            content: prev.content.map(u =>
              u.id === updated.id ? updated : u
            ),
          }
        : prev
    );
  };

  // --- pagination helpers ---

  const canPrev = pageIndex > 0;
  const canNext = pageIndex < page.totalPages - 1;

  // --- UI ---

  return (
    <div className="space-y-6">

      <h1 className="text-xl font-bold">
        Admin — Users
      </h1>

      {/* Filters */}

      <div className="flex gap-4 flex-wrap">

        {/* Tenant */}

        <select
          value={tenantFilter}
          onChange={e => {
            setPageIndex(0);
            setTenantFilter(e.target.value);
          }}
          className="border p-2"
        >
          <option value="">All tenants</option>
          {tenants.map(t => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>

        {/* Status */}

        <select
          value={statusFilter}
          onChange={e => {
            setPageIndex(0);
            setStatusFilter(e.target.value);
          }}
          className="border p-2"
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">ACTIVE</option>
          <option value="LOCKED">LOCKED</option>
          <option value="DISABLED">DISABLED</option>
        </select>

        {/* Email */}

        <input
          placeholder="Search email…"
          value={emailFilter}
          onChange={e => {
            setPageIndex(0);
            setEmailFilter(e.target.value);
          }}
          className="border p-2 flex-1 min-w-50"
        />

      </div>

      {/* Table */}

      <table className="w-full border">
        <thead>
          <tr>
            <th>Email</th>
            <th>Status</th>
            <th>Roles</th>
            <th>Actions</th>
          </tr>
        </thead>

        <tbody>
          {page.content.map(u => (
            <AdminUserRow
              key={u.id}
              user={u}
              allRoles={roles}
              onUserUpdated={updateUser}
            />
          ))}
        </tbody>
      </table>

      {/* Pagination */}

      <div className="flex items-center gap-4">

        <button
          disabled={!canPrev}
          onClick={() => setPageIndex(p => p - 1)}
          className="border px-3 py-1 disabled:opacity-40"
        >
          Prev
        </button>

        <span>
          Page {pageIndex + 1} / {page.totalPages}
        </span>

        <button
          disabled={!canNext}
          onClick={() => setPageIndex(p => p + 1)}
          className="border px-3 py-1 disabled:opacity-40"
        >
          Next
        </button>

      </div>

    </div>
  );
}
