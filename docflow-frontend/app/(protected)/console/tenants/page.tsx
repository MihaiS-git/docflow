"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useState,
  type ChangeEvent,
} from "react";

import { useQueryClient, useMutation } from "@tanstack/react-query";

import { useAuth } from "@/lib/auth/useAuth";
import { ApiError } from "@/lib/apiErrors";
import { createTenant } from "@/lib/admin/adminTenants";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";

import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";

import { TenantRow } from "./TenantRow";

import TableToolbar from "@/components/ui/TableToolbar";
import DataTable from "@/components/ui/DataTable";
import SortableHeader from "@/components/ui/SortableHeader";
import TableColumnWidths from "@/components/ui/TableColumnWidths";
import DataTableFooter from "@/components/ui/DataTableFooter";

import CreateTenantCard from "./CreateTenantCard";
import { useDebouncedPrefixFilter } from "@/hooks/useDebouncedPrefixFilter";
import { useTenantsQuery } from "@/hooks/admin/useTenantsQuery";
import { safeParam } from "@/lib/utils/queryParams";
import { ALL_TENANT_STATUSES_OPTION, TENANT_STATUS_OPTIONS, TenantStatusFilter } from "@/types/admin/Tenant";

const PAGE_SIZE = 20;

type TenantFiltersState = {
  search: string;
  managerName: string;
  managerEmail: string;
  status: TenantStatusFilter;
  dataRegion: string;
  createdAfter: string;
  createdBefore: string;
};

type TenantFiltersAction =
  | {
      type: "SET_FIELD";
      field: keyof TenantFiltersState;
      value: string;
    }
  | { type: "RESET" };

const initialFilters: TenantFiltersState = {
  search: "",
  managerName: "",
  managerEmail: "",
  status: "",
  dataRegion: "",
  createdAfter: "",
  createdBefore: "",
};

function tenantFiltersReducer(
  state: TenantFiltersState,
  action: TenantFiltersAction,
): TenantFiltersState {
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

export default function TenantsPage() {
  const queryClient = useQueryClient();
  const { status, identity } = useAuth();

  const isAdmin =
    status === "AUTH" && identity?.roles?.includes("ADMIN") === true;

  const [error, setError] = useState<string | null>(null);

  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [page, setPage] = useState(0);

  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");

  const [filters, dispatchFilters] = useReducer(
    tenantFiltersReducer,
    initialFilters,
  );

  const searchFilter = useDebouncedPrefixFilter(filters.search);

  const { tenants, pageInfo, isLoading, isFetching, isSuccess } =
    useTenantsQuery({
      page,
      pageSize,
      sort,
      direction,
      status: filters.status || undefined,
      name: safeParam(searchFilter.debounced, false),
      dataRegion: safeParam(filters.dataRegion, false),
      managerName: safeParam(filters.managerName, false),
      managerEmail: safeParam(filters.managerEmail, false),
      createdAfter: filters.createdAfter || undefined,
      createdBefore: filters.createdBefore || undefined,
      enabled: isAdmin,
    });

  useEffect(() => {
    if (!isSuccess) return;

    const isOnlySearchActive =
      searchFilter.debounced !== "" &&
      !filters.managerName.trim() &&
      !filters.managerEmail.trim() &&
      !filters.dataRegion.trim() &&
      !filters.createdAfter &&
      !filters.createdBefore;

    if (isOnlySearchActive) {
      searchFilter.registerResult(tenants.length);
    } else {
      searchFilter.reset();
    }
  }, [
    filters.createdAfter,
    filters.createdBefore,
    filters.dataRegion,
    filters.managerEmail,
    filters.managerName,
    isSuccess,
    searchFilter,
    tenants.length,
  ]);

  const invalidateTenants = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ["tenants"] });
  }, [queryClient]);

  const createTenantMutation = useMutation({
    mutationFn: async (data: {
      name: string;
      description?: string;
      dataRegion?: string;
      retentionDays?: number;
    }) => {
      await createTenant(
        data.name,
        data.description,
        data.dataRegion,
        data.retentionDays,
      );
    },
    onSuccess: async () => {
      setError(null);
      await invalidateTenants();
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) setError(err.message);
      else if (err instanceof Error) setError(err.message);
      else setError("Unexpected error occurred.");
    },
  });

  const handleCreate = useCallback(
    async (data: {
      name: string;
      description?: string;
      dataRegion?: string;
      retentionDays?: number;
    }) => {
      await createTenantMutation.mutateAsync(data);
    },
    [createTenantMutation],
  );

  const handleSort = useCallback((field: string) => {
    setPage(0);
    setSort((prev) => {
      if (prev === field) {
        setDirection((current) => (current === "ASC" ? "DESC" : "ASC"));
        return prev;
      }

      setDirection("ASC");
      return field;
    });
  }, []);

  const resetFilters = useCallback(() => {
    setPage(0);
    dispatchFilters({ type: "RESET" });
    searchFilter.reset();
  }, [searchFilter]);

  const handleSearchChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    setPage(0);
    dispatchFilters({
      type: "SET_FIELD",
      field: "search",
      value: e.target.value,
    });
  }, []);

  const handleStatusChange = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => {
      setPage(0);
      dispatchFilters({
        type: "SET_FIELD",
        field: "status",
        value: e.target.value,
      });
    },
    [],
  );

  const handleManagerNameChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "managerName",
        value: e.target.value,
      });
      setPage(0);
    },
    [],
  );

  const handleManagerEmailChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "managerEmail",
        value: e.target.value,
      });
      setPage(0);
    },
    [],
  );

  const handleDataRegionChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "dataRegion",
        value: e.target.value,
      });
      setPage(0);
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
      setPage(0);
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
      setPage(0);
    },
    [],
  );

  const rows = useMemo(
    () =>
      tenants.map((tenant) => <TenantRow key={tenant.id} tenant={tenant} />),
    [tenants],
  );

  const columnWidths = useMemo(
    () => (
      <TableColumnWidths
        widths={["22%", "10%", "16%", "10%", "10%", "12%", "12%", "8%"]}
      />
    ),
    [],
  );

  const tableHeader = useMemo(
    () => (
      <thead className="sticky top-0 z-10 bg-(--color-table-header) text-(--color-text-secondary) shadow-sm">
        <tr>
          <SortableHeader
            label="Tenant"
            field="name"
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
            label="Manager"
            field="managerName"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Members"
            field="membersCount"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Region"
            field="dataRegion"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Retention"
            field="retentionDays"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Last Activity"
            field="lastActivity"
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
          label="Tenant name"
          value={filters.search}
          onChange={handleSearchChange}
          className="w-full sm:w-56"
        />
        <div className="w-full sm:w-40">
          <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
            <span>Status</span>
            <Select value={filters.status} onChange={handleStatusChange}>
              {TENANT_STATUS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </Select>
          </label>
        </div>
        <Input
          label="Manager name"
          value={filters.managerName}
          onChange={handleManagerNameChange}
          className="w-full sm:w-44"
        />
        <Input
          label="Manager email"
          value={filters.managerEmail}
          onChange={handleManagerEmailChange}
          className="w-full sm:w-52"
        />
        <Input
          label="Region"
          value={filters.dataRegion}
          onChange={handleDataRegionChange}
          className="w-full sm:w-32"
        />
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
      handleStatusChange,
      handleManagerNameChange,
      handleManagerEmailChange,
      handleDataRegionChange,
      handleCreatedAfterChange,
      handleCreatedBeforeChange,
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
        title="Admin — Tenants"
        description="Manage organization tenants, governance settings, and lifecycle."
      />

      <div className="flex flex-col gap-6">
        <CreateTenantCard
          loading={createTenantMutation.isPending}
          error={error}
          onSubmit={handleCreate}
        />

        <Card title="Filters">
          <TableToolbar filters={filtersUI} />
        </Card>

        <Card>
          <DataTable
            loading={isLoading && tenants.length === 0}
            empty={!isLoading && tenants.length === 0}
            emptyMessage="No tenants found"
            className={
              isFetching && tenants.length > 0
                ? "pointer-events-none opacity-70 transition-opacity"
                : ""
            }
            footer={
              pageInfo ? (
                <DataTableFooter
                  page={page}
                  pageSize={pageSize}
                  totalPages={pageInfo.totalPages}
                  totalElements={pageInfo.totalElements}
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
            {rows}
          </DataTable>
        </Card>
      </div>
    </PageContainer>
  );
}
