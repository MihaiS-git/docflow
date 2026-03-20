"use client";

import {
  useCallback,
  useMemo,
  useReducer,
  useState,
  type ChangeEvent,
} from "react";

import { useAuth } from "@/lib/auth/useAuth";
import { ApiError } from "@/lib/apiErrors";
import { fetchAllTenants, createTenant } from "@/lib/admin/adminTenants";

import type { AdminTenant } from "@/types/admin/Tenant";
import type { SpringPage } from "@/types/api/SpringPage";

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
import { usePaginatedAdminTable } from "@/hooks/usePaginatedAdminTable";
import { useDebouncedPrefixFilter } from "@/hooks/useDebouncedPrefixFilter";

const PAGE_SIZE = 20;

type TenantFiltersState = {
  search: string;
  managerName: string;
  managerEmail: string;
  status: string;
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
  status: "ACTIVE",
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
  const { status, identity } = useAuth();

  const isAdmin =
    status === "AUTH" && identity?.roles?.includes("ADMIN") === true;

  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [createLoading, setCreateLoading] = useState(false);

  const [page, setPage] = useState(0);

  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");

  const [filters, dispatchFilters] = useReducer(
    tenantFiltersReducer,
    initialFilters,
  );

  /**
   * Debounced prefix filters
   */
  const searchFilter = useDebouncedPrefixFilter(filters.search);
  const managerNameFilter = useDebouncedPrefixFilter(filters.managerName);
  const managerEmailFilter = useDebouncedPrefixFilter(filters.managerEmail);

  const queryKey = JSON.stringify({
    page,
    sort,
    direction,
    status: filters.status,
    search: searchFilter.debounced,
    managerName: managerNameFilter.debounced,
    managerEmail: managerEmailFilter.debounced,
    dataRegion: filters.dataRegion,
    createdAfter: filters.createdAfter,
    createdBefore: filters.createdBefore,
  });

  const loader = useCallback(async (): Promise<SpringPage<AdminTenant>> => {
    const empty: SpringPage<AdminTenant> = {
      content: [],
      number: page,
      size: PAGE_SIZE,
      totalElements: 0,
      totalPages: 0,
    };

    if (
      searchFilter.shouldBlock() ||
      managerNameFilter.shouldBlock() ||
      managerEmailFilter.shouldBlock()
    ) {
      return empty;
    }

    const result = await fetchAllTenants(page, PAGE_SIZE, sort, direction, {
      status: filters.status,
      name: searchFilter.debounced || undefined,
      dataRegion: filters.dataRegion || undefined,
      managerName: managerNameFilter.debounced || undefined,
      managerEmail: managerEmailFilter.debounced || undefined,
      createdAfter: filters.createdAfter || undefined,
      createdBefore: filters.createdBefore || undefined,
    });

    searchFilter.registerResult(result.content.length);
    managerNameFilter.registerResult(result.content.length);
    managerEmailFilter.registerResult(result.content.length);

    return result;
  }, [
    page,
    sort,
    direction,
    filters.status,
    filters.dataRegion,
    filters.createdAfter,
    filters.createdBefore,
    searchFilter,
    managerNameFilter,
    managerEmailFilter,
  ]);

  const { data, loading, isPending, reload } = usePaginatedAdminTable(loader, {
    enabled: isAdmin,
    queryKey,
    resetKeys: [
      searchFilter.debounced,
      managerNameFilter.debounced,
      managerEmailFilter.debounced,
      filters.status,
      filters.dataRegion,
      filters.createdAfter,
      filters.createdBefore,
    ],
  });

  const tenants = useMemo(() => data?.content ?? [], [data?.content]);

  const handleCreate = useCallback(
    async (e: React.FormEvent<HTMLFormElement>) => {
      e.preventDefault();

      if (!name.trim()) {
        setError("Tenant name is required.");
        return;
      }

      setCreateLoading(true);
      setError(null);

      try {
        await createTenant(name.trim());
        setName("");
        reload();
      } catch (err) {
        if (err instanceof ApiError) setError(err.message);
        else if (err instanceof Error) setError(err.message);
        else setError("Unexpected error occurred.");
      } finally {
        setCreateLoading(false);
      }
    },
    [name, reload],
  );

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
    managerNameFilter.reset();
    managerEmailFilter.reset();

    setPage(0);
  }, [searchFilter, managerNameFilter, managerEmailFilter]);

  const handleSearchChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    dispatchFilters({
      type: "SET_FIELD",
      field: "search",
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

  const handleManagerNameChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      dispatchFilters({
        type: "SET_FIELD",
        field: "managerName",
        value: e.target.value,
      });
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

  const rows = useMemo(
    () =>
      tenants.map((tenant) => (
        <TenantRow key={tenant.id} tenant={tenant} onUpdated={reload} />
      )),
    [tenants, reload],
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
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
              <option value="TERMINATED">Terminated</option>
              <option value="ALL">All</option>
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
          name={name}
          loading={createLoading}
          error={error}
          setName={setName}
          onSubmit={handleCreate}
        />

        <Card title="Filters">
          <TableToolbar filters={filtersUI} />
        </Card>

        <Card>
          <DataTable
            loading={loading && tenants.length === 0}
            empty={!loading && tenants.length === 0}
            emptyMessage="No tenants found"
            className={
              (loading || isPending) && tenants.length > 0
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
