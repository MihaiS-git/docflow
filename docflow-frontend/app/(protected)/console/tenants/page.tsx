"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth/useAuth";
import { ApiError } from "@/lib/apiErrors";
import { fetchAllTenants, createTenant } from "@/lib/admin/adminTenants";
import type { AdminTenant } from "@/types/admin/Tenant";

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

export default function TenantsPage() {
  const { status, identity } = useAuth();
  const isAdmin = status === "AUTH" && identity?.roles.includes("ADMIN");

  const [tenants, setTenants] = useState<AdminTenant[]>([]);
  const [name, setName] = useState("");

  const [tableLoading, setTableLoading] = useState(false);
  const [createLoading, setCreateLoading] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ACTIVE");
  const [dataRegionFilter, setDataRegionFilter] = useState("");
  const [managerNameFilter, setManagerNameFilter] = useState("");
  const [managerEmailFilter, setManagerEmailFilter] = useState("");
  const [createdAfterFilter, setCreatedAfterFilter] = useState("");
  const [createdBeforeFilter, setCreatedBeforeFilter] = useState("");

  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");

  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [debouncedManagerName, setDebouncedManagerName] = useState("");
  const [debouncedManagerEmail, setDebouncedManagerEmail] = useState("");

  const requestIdRef = useRef(0);

  const lastEmptySearchRef = useRef<string | null>(null);
  const lastEmptyManagerRef = useRef<string | null>(null);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 350);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    const timer = setTimeout(
      () => setDebouncedManagerName(managerNameFilter),
      350
    );
    return () => clearTimeout(timer);
  }, [managerNameFilter]);

  useEffect(() => {
    const timer = setTimeout(
      () => setDebouncedManagerEmail(managerEmailFilter),
      350
    );
    return () => clearTimeout(timer);
  }, [managerEmailFilter]);

  const loadTenants = useCallback(async () => {
    // skip request when narrowing a known empty result
    if (
      lastEmptySearchRef.current &&
      debouncedSearch.startsWith(lastEmptySearchRef.current)
    ) {
      setTenants([]);
      setTotalPages(0);
      setTotalElements(0);
      return;
    }

    if (
      lastEmptyManagerRef.current &&
      debouncedManagerName.startsWith(lastEmptyManagerRef.current)
    ) {
      setTenants([]);
      setTotalPages(0);
      setTotalElements(0);
      return;
    }

    const requestId = ++requestIdRef.current;
    setTableLoading(true);

    try {
      const res = await fetchAllTenants(page, size, sort, direction, {
        status: statusFilter,
        name: debouncedSearch || undefined,
        dataRegion: dataRegionFilter || undefined,
        managerName: debouncedManagerName || undefined,
        managerEmail: debouncedManagerEmail || undefined,
        createdAfter: createdAfterFilter || undefined,
        createdBefore: createdBeforeFilter || undefined,
      });

      if (requestId !== requestIdRef.current) return;

      const rows = res.content ?? [];
      const nextTotalPages = res.totalPages ?? 0;

      setTenants(rows);
      setTotalPages(nextTotalPages);
      setTotalElements(res.totalElements ?? 0);

      if (rows.length === 0) {
        lastEmptySearchRef.current = debouncedSearch || null;
        lastEmptyManagerRef.current = debouncedManagerName || null;
      } else {
        lastEmptySearchRef.current = null;
        lastEmptyManagerRef.current = null;
      }

      if (page >= nextTotalPages && nextTotalPages > 0) {
        setPage(nextTotalPages - 1);
      }
    } finally {
      if (requestId === requestIdRef.current) {
        setTableLoading(false);
      }
    }
  }, [
    page,
    size,
    sort,
    direction,
    debouncedSearch,
    debouncedManagerName,
    debouncedManagerEmail,
    statusFilter,
    dataRegionFilter,
    createdAfterFilter,
    createdBeforeFilter,
  ]);

  useEffect(() => {
    if (!isAdmin) return;
    loadTenants();
  }, [isAdmin, loadTenants]);

  useEffect(() => {
    setPage(0);
  }, [
    debouncedSearch,
    debouncedManagerName,
    debouncedManagerEmail,
    statusFilter,
    dataRegionFilter,
    createdAfterFilter,
    createdBeforeFilter,
  ]);

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
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
      await loadTenants();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else if (err instanceof Error) setError(err.message);
      else setError("Unexpected error occurred.");
    } finally {
      setCreateLoading(false);
    }
  }

  function handleSort(field: string) {
    if (sort === field) {
      setDirection((d) => (d === "ASC" ? "DESC" : "ASC"));
    } else {
      setSort(field);
      setDirection("ASC");
    }

    setPage(0);
  }

  function resetFilters() {
    setSearch("");
    setDebouncedSearch("");
    setStatusFilter("ACTIVE");
    setDataRegionFilter("");
    setManagerNameFilter("");
    setManagerEmailFilter("");
    setDebouncedManagerName("");
    setDebouncedManagerEmail("");
    setCreatedAfterFilter("");
    setCreatedBeforeFilter("");

    lastEmptySearchRef.current = null;
    lastEmptyManagerRef.current = null;

    setPage(0);
  }

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
          <TableToolbar
            filters={
              <form
                className="flex flex-wrap gap-4 items-end"
                onSubmit={(e) => {
                  e.preventDefault();
                  loadTenants();
                }}
              >
                <Input
                  label="Tenant name"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="w-full sm:w-56"
                />

                <div className="w-full sm:w-40">
                  <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
                    <span>Status</span>
                    <Select
                      value={statusFilter}
                      onChange={(e) => setStatusFilter(e.target.value)}
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="SUSPENDED">Suspended</option>
                      <option value="TERMINATED">Terminated</option>
                      <option value="ALL">All</option>
                    </Select>
                  </label>
                </div>

                <Input
                  label="Manager name"
                  value={managerNameFilter}
                  onChange={(e) => setManagerNameFilter(e.target.value)}
                  className="w-full sm:w-44"
                />

                <Input
                  label="Manager email"
                  value={managerEmailFilter}
                  onChange={(e) => setManagerEmailFilter(e.target.value)}
                  className="w-full sm:w-52"
                />

                <Input
                  label="Region"
                  value={dataRegionFilter}
                  onChange={(e) => setDataRegionFilter(e.target.value)}
                  className="w-full sm:w-32"
                />

                <Input
                  label="Created after"
                  type="date"
                  value={createdAfterFilter}
                  onChange={(e) => setCreatedAfterFilter(e.target.value)}
                  className="w-full sm:w-40"
                />

                <Input
                  label="Created before"
                  type="date"
                  value={createdBeforeFilter}
                  onChange={(e) => setCreatedBeforeFilter(e.target.value)}
                  className="w-full sm:w-40"
                />

                <Button type="button" variant="ghost" onClick={resetFilters}>
                  Reset
                </Button>
              </form>
            }
          />
        </Card>

        <div id="tenants-table">
          <Card>
            <DataTable
              loading={tableLoading && tenants.length === 0}
              empty={!tableLoading && tenants.length === 0}
              emptyMessage="No tenants found"
              className={
                tableLoading
                  ? "opacity-70 pointer-events-none transition-opacity"
                  : ""
              }
              footer={
                <DataTableFooter
                  page={page}
                  pageSize={size}
                  totalPages={totalPages}
                  totalElements={totalElements}
                  onPageChange={(p) => {
                    setPage(p);
                    document
                      .getElementById("tenants-table")
                      ?.scrollIntoView({ behavior: "smooth" });
                  }}
                />
              }
            >
              <TableColumnWidths
                widths={[
                  "22%",
                  "10%",
                  "16%",
                  "10%",
                  "10%",
                  "12%",
                  "12%",
                  "8%",
                ]}
              />

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

              <tbody className="bg-(--color-table-row)">
                {tenants.map((tenant) => (
                  <TenantRow
                    key={tenant.id}
                    tenant={tenant}
                    onUpdated={loadTenants}
                  />
                ))}
              </tbody>
            </DataTable>
          </Card>
        </div>
      </div>
    </PageContainer>
  );
}