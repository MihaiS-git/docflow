"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useState,
  type ChangeEvent,
} from "react";
import { useParams } from "next/navigation";

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

import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

import { usePaginatedAdminTable } from "@/hooks/usePaginatedAdminTable";
import { useDebouncedPrefixFilter } from "@/hooks/useDebouncedPrefixFilter";

import type { TenantResponseDTO } from "@/types/admin/Tenant";
import type { SpringPage } from "@/types/api/SpringPage";
import type { TenantUser } from "@/types/admin/TenantUser";

const PAGE_SIZE = 20;

type FiltersState = {
  search: string;
  jobTitle: string;
  department: string;
  role: string;
  status: string;
  createdAfter: string;
  createdBefore: string;
};

type FiltersAction =
  | {
      type: "SET_FIELD";
      field: keyof FiltersState;
      value: string;
    }
  | { type: "RESET" };

const initialFilters: FiltersState = {
  search: "",
  jobTitle: "",
  department: "",
  role: "",
  status: "",
  createdAfter: "",
  createdBefore: "",
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

export default function TenantDetailsPage() {
  const { tenantId } = useParams<{ tenantId: string }>();

  const [tenant, setTenant] = useState<TenantResponseDTO | null>(null);
  const [tenantLoading, setTenantLoading] = useState(true);
  const [tenantError, setTenantError] = useState<string | null>(null);

  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");

  const [filters, dispatchFilters] = useReducer(filtersReducer, initialFilters);

  const searchFilter = useDebouncedPrefixFilter(filters.search);
  const jobTitleFilter = useDebouncedPrefixFilter(filters.jobTitle);
  const departmentFilter = useDebouncedPrefixFilter(filters.department);

  useEffect(() => {
    if (!tenantId) return;

    let cancelled = false;

    async function loadTenant() {
      setTenantLoading(true);
      setTenantError(null);

      try {
        const res = await apiFetch<TenantResponseDTO>(
          `/api/admin/tenants/${tenantId}`,
        );

        if (!cancelled) {
          setTenant(res);
        }
      } catch (err) {
        if (cancelled) return;

        if (err instanceof ApiError) setTenantError(err.message);
        else if (err instanceof Error) setTenantError(err.message);
        else setTenantError("Unexpected error");
      } finally {
        if (!cancelled) {
          setTenantLoading(false);
        }
      }
    }

    void loadTenant();

    return () => {
      cancelled = true;
    };
  }, [tenantId]);

  useEffect(() => {
    searchFilter.reset();
    jobTitleFilter.reset();
    departmentFilter.reset();
  }, [
    filters.role,
    filters.status,
    filters.createdAfter,
    filters.createdBefore,
    sort,
    direction,
    searchFilter,
    jobTitleFilter,
    departmentFilter,
  ]);

  const queryKey = JSON.stringify({
    tenantId,
    page,
    sort,
    direction,
    search: searchFilter.debounced,
    jobTitle: jobTitleFilter.debounced,
    department: departmentFilter.debounced,
    role: filters.role,
    status: filters.status,
    createdAfter: filters.createdAfter,
    createdBefore: filters.createdBefore,
  });

  const loader = useCallback(async (): Promise<SpringPage<TenantUser>> => {
    const empty: SpringPage<TenantUser> = {
      content: [],
      page: {
        number: page,
        size: pageSize,
        totalElements: 0,
        totalPages: 0,
      },
    };

    if (
      searchFilter.shouldBlock() ||
      jobTitleFilter.shouldBlock() ||
      departmentFilter.shouldBlock()
    ) {
      return empty;
    }

    const params = new URLSearchParams();
    params.append("page", String(page));
    params.append("size", String(pageSize));
    params.append("sort", sort);
    params.append("direction", direction);

    if (searchFilter.debounced) {
      params.append("search", searchFilter.debounced);
    }

    if (jobTitleFilter.debounced) {
      params.append("jobTitle", jobTitleFilter.debounced);
    }

    if (departmentFilter.debounced) {
      params.append("department", departmentFilter.debounced);
    }

    if (filters.role) {
      params.append("role", filters.role);
    }

    if (filters.status) {
      params.append("status", filters.status);
    }

    if (filters.createdAfter) {
      params.append("createdAfter", filters.createdAfter);
    }

    if (filters.createdBefore) {
      params.append("createdBefore", filters.createdBefore);
    }

    const result = await apiFetch<SpringPage<TenantUser>>(
      `/api/admin/tenants/${tenantId}/users?${params.toString()}`,
    );

    searchFilter.registerResult(result.content.length);
    jobTitleFilter.registerResult(result.content.length);
    departmentFilter.registerResult(result.content.length);

    return result;
  }, [
    page,
    searchFilter,
    jobTitleFilter,
    departmentFilter,
    pageSize,
    sort,
    direction,
    filters.role,
    filters.status,
    filters.createdAfter,
    filters.createdBefore,
    tenantId,
  ]);

  const { data, loading, isPending } = usePaginatedAdminTable(loader, {
    enabled: Boolean(tenantId),
    queryKey,
    resetKeys: [
      searchFilter.debounced,
      jobTitleFilter.debounced,
      departmentFilter.debounced,
      filters.role,
      filters.status,
      filters.createdAfter,
      filters.createdBefore,
    ],
  });

  const users = useMemo(() => data?.content ?? [], [data?.content]);

  const handleSort = useCallback((field: string) => {
    setSort((prev) => {
      if (prev === field) {
        setDirection((current) => (current === "ASC" ? "DESC" : "ASC"));
        return prev;
      }

      setDirection("ASC");
      return field;
    });

    setPage(0);
  }, []);

  const resetFilters = useCallback(() => {
    dispatchFilters({ type: "RESET" });

    searchFilter.reset();
    jobTitleFilter.reset();
    departmentFilter.reset();

    setPage(0);
  }, [searchFilter, jobTitleFilter, departmentFilter]);

  const handleSearchChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    dispatchFilters({
      type: "SET_FIELD",
      field: "search",
      value: e.target.value,
    });
  }, []);

  const handleJobTitleChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "jobTitle",
        value: e.target.value,
      });
    },
    [],
  );

  const handleDepartmentChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "department",
        value: e.target.value,
      });
    },
    [],
  );

  const handleRoleChange = useCallback((e: ChangeEvent<HTMLSelectElement>) => {
    dispatchFilters({
      type: "SET_FIELD",
      field: "role",
      value: e.target.value,
    });
  }, []);

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

  const handleCreatedAfterChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "createdAfter",
        value: e.target.value,
      });
    },
    [],
  );

  const handleCreatedBeforeChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "createdBefore",
        value: e.target.value,
      });
    },
    [],
  );

  const columnWidths = useMemo(
    () => (
      <TableColumnWidths
        widths={["24%", "14%", "14%", "12%", "12%", "12%", "12%"]}
      />
    ),
    [],
  );

  const tableHeader = useMemo(
    () => (
      <thead className="sticky top-0 z-10 bg-(--color-table-header) text-(--color-text-secondary) shadow-sm">
        <tr>
          <SortableHeader
            label="User"
            field="displayName"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Job title"
            field="jobTitle"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Department"
            field="department"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Role"
            field="role"
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
          <SortableHeader
            label="Created"
            field="createdAt"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Updated"
            field="updatedAt"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
        </tr>
      </thead>
    ),
    [sort, direction, handleSort],
  );

  const rows = useMemo(
    () =>
      users.map((u) => (
        <tr
          key={u.userId}
          className="border-b border-(--color-table-border) align-top hover:bg-(--color-table-row-hover)"
        >
          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            <div className="flex flex-col">
              <span>{u.displayName}</span>
              <span className="text-xs text-(--color-text-muted)">
                {u.email}
              </span>
              {u.businessPhone && (
                <span className="text-xs text-(--color-text-muted)">
                  {u.businessPhone}
                </span>
              )}
            </div>
          </td>

          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            {u.jobTitle || "—"}
          </td>

          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            {u.department || "—"}
          </td>

          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            {u.role}
          </td>

          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            {u.status}
          </td>

          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            {new Date(u.createdAt).toLocaleDateString()}
          </td>

          <td className="px-4 py-3 text-sm text-(--color-text-primary)">
            {new Date(u.updatedAt).toLocaleDateString()}
          </td>
        </tr>
      )),
    [users],
  );

  const filtersUI = useMemo(
    () => (
      <form className="flex flex-wrap items-end gap-4">
        <Input
          label="Search"
          value={filters.search}
          onChange={handleSearchChange}
          className="w-full sm:w-56"
        />

        <Input
          label="Job title"
          value={filters.jobTitle}
          onChange={handleJobTitleChange}
          className="w-full sm:w-44"
        />

        <Input
          label="Department"
          value={filters.department}
          onChange={handleDepartmentChange}
          className="w-full sm:w-44"
        />

        <div className="w-full sm:w-40">
          <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
            <span>Role</span>
            <Select value={filters.role} onChange={handleRoleChange}>
              <option value="">All</option>
              <option value="MANAGER">MANAGER</option>
              <option value="MEMBER">MEMBER</option>
              <option value="EXECUTOR">EXECUTOR</option>
              <option value="REVIEWER">REVIEWER</option>
            </Select>
          </label>
        </div>

        <div className="w-full sm:w-40">
          <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
            <span>Status</span>
            <Select value={filters.status} onChange={handleStatusChange}>
              <option value="">All</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="SUSPENDED">SUSPENDED</option>
            </Select>
          </label>
        </div>

        <Input
          label="Created after"
          type="date"
          value={filters.createdAfter}
          onChange={handleCreatedAfterChange}
          className="w-full sm:w-40"
        />

        <Input
          label="Created before"
          type="date"
          value={filters.createdBefore}
          onChange={handleCreatedBeforeChange}
          className="w-full sm:w-40"
        />

        <Button type="button" variant="outline" onClick={resetFilters}>
          Reset
        </Button>
      </form>
    ),
    [
      filters,
      handleSearchChange,
      handleJobTitleChange,
      handleDepartmentChange,
      handleRoleChange,
      handleStatusChange,
      handleCreatedAfterChange,
      handleCreatedBeforeChange,
      resetFilters,
    ],
  );

  return (
    <PageContainer>
      <PageHeader
        title="Tenant details"
        description="Inspect tenant configuration and users."
      />

      {tenantLoading && <p className="text-sm">Loading…</p>}

      {tenantError && (
        <p className="text-sm text-(--color-error)">{tenantError}</p>
      )}

      {tenant && (
        <div className="flex flex-col gap-6">
          <Card title="Overview">
            <div className="grid grid-cols-1 gap-x-4 gap-y-2 text-sm md:grid-cols-2">
              <p>
                <strong>Name:</strong> {tenant.name || "-"}
              </p>
              <p>
                <strong>Owner:</strong> {tenant.ownerDisplayName || "-"}
              </p>
              <p>
                <strong>Status:</strong> {tenant.status || "-"}
              </p>
              <p>
                <strong>Region:</strong> {tenant.dataRegion || "-"}
              </p>
              <p>
                <strong>Retention:</strong>{" "}
                {tenant.retentionDays === 0
                  ? "unlimited"
                  : tenant.retentionDays}{" "}
                days
              </p>
              <p>
                <strong>Description:</strong> {tenant.description || "-"}
              </p>
              <p>
                <strong>Created at:</strong>{" "}
                {new Date(tenant.createdAt).toLocaleDateString()}
              </p>
              <p>
                <strong>Updated at:</strong>{" "}
                {new Date(tenant.updatedAt).toLocaleDateString()}
              </p>
            </div>
          </Card>

          <Card title="Filters">
            <TableToolbar filters={filtersUI} />
          </Card>

          <Card>
            <DataTable
              loading={loading && users.length === 0}
              empty={!loading && users.length === 0}
              emptyMessage="No users found"
              className={
                (loading || isPending) && users.length > 0
                  ? "pointer-events-none opacity-70 transition-opacity"
                  : ""
              }
              footer={
                data ? (
                  <DataTableFooter
                    page={page}
                    pageSize={pageSize}
                    totalPages={data.page.totalPages}
                    totalElements={data.page.totalElements}
                    onPageChange={setPage}
                    onPageSizeChange={(size) => {
                      setPageSize(size);
                      setPage(0);
                    }}
                  />
                ) : null
              }
            >
              {columnWidths}
              {tableHeader}
              <tbody>{rows}</tbody>
            </DataTable>
          </Card>
        </div>
      )}
    </PageContainer>
  );
}
