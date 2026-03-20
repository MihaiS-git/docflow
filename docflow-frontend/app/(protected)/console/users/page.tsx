"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useState,
  type ChangeEvent,
} from "react";

import { useAuth } from "@/lib/auth/useAuth";
import { fetchAdminUsers } from "@/lib/admin/adminUsers";
import { fetchAdminRoles } from "@/lib/admin/adminRoles";
import { fetchManagedTenants } from "@/lib/admin/adminTenants";

import type { AdminUser } from "@/types/admin/AdminUser";
import type { SpringPage } from "@/types/api/SpringPage";
import type { AdminTenant } from "@/types/admin/Tenant";

import { AdminUserRow } from "./AdminUserRow";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";

import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import Button from "@/components/ui/Button";

import TableToolbar from "@/components/ui/TableToolbar";
import DataTable from "@/components/ui/DataTable";
import SortableHeader from "@/components/ui/SortableHeader";
import TableColumnWidths from "@/components/ui/TableColumnWidths";
import DataTableFooter from "@/components/ui/DataTableFooter";

import { usePaginatedAdminTable } from "@/hooks/usePaginatedAdminTable";
import { useDebouncedPrefixFilter } from "@/hooks/useDebouncedPrefixFilter";

const PAGE_SIZE = 20;

type FiltersState = {
  tenantId: string;
  status: string;
  email: string;
};

type FiltersAction =
  | {
      type: "SET_FIELD";
      field: keyof FiltersState;
      value: string;
    }
  | { type: "RESET" };

type OptimisticUsersState = {
  queryKey: string;
  byId: Record<string, AdminUser>;
};

const initialFilters: FiltersState = {
  tenantId: "",
  status: "",
  email: "",
};

const initialOptimisticUsersState: OptimisticUsersState = {
  queryKey: "",
  byId: {},
};

function filtersReducer(
  state: FiltersState,
  action: FiltersAction,
): FiltersState {
  switch (action.type) {
    case "SET_FIELD":
      if (state[action.field] === action.value) return state;

      return {
        ...state,
        [action.field]: action.value,
      };

    case "RESET":
      return initialFilters;

    default:
      return state;
  }
}

function emptyUsersPage(page: number): SpringPage<AdminUser> {
  return {
    content: [],
    number: page,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  };
}

export default function AdminUsersPage() {
  const { status, identity } = useAuth();
  const isAdmin =
    status === "AUTH" && identity?.roles?.includes("ADMIN") === true;

  const [page, setPage] = useState(0);

  const [roles, setRoles] = useState<string[]>([]);
  const [tenants, setTenants] = useState<AdminTenant[]>([]);

  const [sort, setSort] = useState("email");
  const [direction, setDirection] = useState<"ASC" | "DESC">("ASC");

  const [filters, dispatchFilters] = useReducer(
    filtersReducer,
    initialFilters,
  );

  const [optimisticUsers, setOptimisticUsers] = useState<OptimisticUsersState>(
    initialOptimisticUsersState,
  );

  /**
   * Debounced + prefix guarded email filter
   */
  const emailFilter = useDebouncedPrefixFilter(filters.email);

  useEffect(() => {
    if (!isAdmin) return;

    let cancelled = false;

    async function loadStatic() {
      const [tenantsData, rolesData] = await Promise.all([
        fetchManagedTenants(),
        fetchAdminRoles(),
      ]);

      if (cancelled) return;

      setTenants(tenantsData ?? []);
      setRoles(rolesData ?? []);
    }

    void loadStatic();

    return () => {
      cancelled = true;
    };
  }, [isAdmin]);

  /**
   * Reset prefix guard when non-email filters change
   */
  useEffect(() => {
    emailFilter.reset();
  }, [filters.tenantId, filters.status, sort, direction, emailFilter]);

  const queryKey = JSON.stringify({
    page,
    sort,
    direction,
    tenantId: filters.tenantId,
    status: filters.status,
    email: emailFilter.debounced,
  });

  const loader = useCallback(async (): Promise<SpringPage<AdminUser>> => {
    if (emailFilter.shouldBlock()) {
      return emptyUsersPage(page);
    }

    const result = await fetchAdminUsers(page, PAGE_SIZE, sort, direction, {
      tenantId: filters.tenantId || undefined,
      status: filters.status || undefined,
      email: emailFilter.debounced || undefined,
    });

    emailFilter.registerResult(result.content.length);

    return result;
  }, [
    page,
    sort,
    direction,
    filters.tenantId,
    filters.status,
    emailFilter,
  ]);

  const { data, loading, isPending } = usePaginatedAdminTable(loader, {
    enabled: isAdmin,
    queryKey,
    resetKeys: [filters.tenantId, filters.status, emailFilter.debounced],
  });

  const users = useMemo(() => {
    const content = data?.content ?? [];

    if (optimisticUsers.queryKey !== queryKey) {
      return content;
    }

    return content.map((user) => optimisticUsers.byId[user.id] ?? user);
  }, [data?.content, optimisticUsers, queryKey]);

  const handleUserUpdated = useCallback(
    (updatedUser: AdminUser) => {
      setOptimisticUsers((prev) => {
        const nextById =
          prev.queryKey === queryKey
            ? {
                ...prev.byId,
                [updatedUser.id]: updatedUser,
              }
            : {
                [updatedUser.id]: updatedUser,
              };

        return {
          queryKey,
          byId: nextById,
        };
      });
    },
    [queryKey],
  );

  const handleSort = useCallback((field: string) => {
    setSort((prev) => {
      if (prev === field) {
        setDirection((currentDirection) =>
          currentDirection === "ASC" ? "DESC" : "ASC",
        );
        return prev;
      }

      setDirection("ASC");
      return field;
    });

    setPage(0);
  }, []);

  const resetFilters = useCallback(() => {
    dispatchFilters({ type: "RESET" });
    emailFilter.reset();
    setPage(0);
  }, [emailFilter]);

  const handleTenantChange = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "tenantId",
        value: e.target.value,
      });
    },
    [],
  );

  const handleStatusChange = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "status",
        value: e.target.value,
      });
    },
    [],
  );

  const handleEmailChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "email",
        value: e.target.value,
      });
    },
    [],
  );

  const rows = users.map((user) => (
    <AdminUserRow
      key={user.id}
      user={user}
      allRoles={roles}
      onUserUpdated={handleUserUpdated}
    />
  ));

  const columnWidths = useMemo(
    () => <TableColumnWidths widths={["35%", "15%", "35%", "15%"]} />,
    [],
  );

  const tableHeader = useMemo(
    () => (
      <thead className="sticky top-0 z-10 bg-(--color-table-header) text-(--color-text-secondary) shadow-sm">
        <tr>
          <SortableHeader
            label="Email"
            field="email"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Status"
            field="status"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide">
            Roles
          </th>
          <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide">
            Actions
          </th>
        </tr>
      </thead>
    ),
    [sort, direction, handleSort],
  );

  const filtersUI = useMemo(
    () => (
      <form className="flex flex-wrap items-end gap-4">
        <div className="w-full sm:w-56">
          <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
            <Select label="Tenant" value={filters.tenantId} onChange={handleTenantChange}>
              <option value="">Select tenant…</option>
              {tenants.map((tenant) => (
                <option key={tenant.id} value={tenant.id}>
                  {tenant.name}
                </option>
              ))}
            </Select>
          </label>
        </div>

        <div className="w-full sm:w-40">
          <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
            <Select label="Status" value={filters.status} onChange={handleStatusChange}>
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="LOCKED">Locked</option>
              <option value="DISABLED">Disabled</option>
            </Select>
          </label>
        </div>

        <Input
          label="Email"
          value={filters.email}
          onChange={handleEmailChange}
          className="w-full sm:w-64"
        />

        <Button type="button" variant="outline" onClick={resetFilters}>
          Reset
        </Button>
      </form>
    ),
    [
      filters.tenantId,
      filters.status,
      filters.email,
      tenants,
      handleTenantChange,
      handleStatusChange,
      handleEmailChange,
      resetFilters,
    ],
  );

  if (status !== "AUTH" || !isAdmin) {
    return (
      <PageContainer>
        <p className="text-sm text-(--color-error)">Access denied</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title="Admin — Users"
        description="Manage user accounts, roles, and account status."
      />

      <div className="flex flex-col gap-6">
        <Card title="Filters">
          <TableToolbar filters={filtersUI} />
        </Card>

        <Card>
          <DataTable
            loading={loading && users.length === 0}
            empty={!loading && users.length === 0}
            emptyMessage="No users found"
            className={
              (isPending || loading) && users.length > 0
                ? "pointer-events-none opacity-70 transition-opacity"
                : ""
            }
            footer={
              data ? (
                <DataTableFooter
                  page={page}
                  pageSize={PAGE_SIZE}
                  totalPages={data.totalPages}
                  totalElements={data.totalElements}
                  onPageChange={setPage}
                />
              ) : null
            }
          >
            {columnWidths}
            {tableHeader}
            {rows}
          </DataTable>
        </Card>
      </div>
    </PageContainer>
  );
}