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

import { useDebouncedPrefixFilter } from "@/hooks/useDebouncedPrefixFilter";

import type { TenantResponseDTO } from "@/types/admin/Tenant";
import { useTenantUsersQuery } from "@/hooks/admin/useTenantUsersQuery";
import { UserRow } from "./UserRow";
import {
  MEMBERSHIP_STATUS_OPTIONS,
  ROLE_OPTIONS_FILTER,
} from "@/lib/admin/tenantUserOptions";

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

  const { data, isLoading, isFetching } = useTenantUsersQuery({
    enabled: Boolean(tenantId),
    tenantId,
    page,
    size: pageSize,
    sort,
    direction,
    search: searchFilter.debounced || undefined,
    jobTitle: jobTitleFilter.debounced || undefined,
    department: departmentFilter.debounced || undefined,
    role: filters.role || undefined,
    status: filters.status || undefined,
    createdAfter: filters.createdAfter || undefined,
    createdBefore: filters.createdBefore || undefined,
  });

  const loading = isLoading;
  const isPending = isFetching;

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
        widths={["22%", "12%", "12%", "10%", "10%", "12%", "12%", "10%"]}
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
        <Input
          label="Manager email"
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
              {ROLE_OPTIONS_FILTER.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </Select>
          </label>
        </div>

        <div className="w-full sm:w-40">
          <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
            <span>Status</span>
            <Select value={filters.status} onChange={handleStatusChange}>
              {MEMBERSHIP_STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
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
              <tbody>
                {users.map((u) => (
                  <UserRow key={u.userId} tenantId={tenantId} user={u} />
                ))}
              </tbody>
            </DataTable>
          </Card>
        </div>
      )}
    </PageContainer>
  );
}
