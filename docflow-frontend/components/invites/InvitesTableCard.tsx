"use client";

import { useCallback, useMemo, useState } from "react";

import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

import type { AdminTenant } from "@/types/admin/Tenant";
import type { SpringPage } from "@/types/api/SpringPage";
import type { InviteRow, InviteStatusFilter } from "@/types/invites/types";

import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import Button from "@/components/ui/Button";

import TableToolbar from "@/components/ui/TableToolbar";
import DataTable from "@/components/ui/DataTable";
import DataTableFooter from "@/components/ui/DataTableFooter";
import TableColumnWidths from "@/components/ui/TableColumnWidths";
import SortableHeader from "@/components/ui/SortableHeader";

import { useDebouncedPrefixFilter } from "@/hooks/useDebouncedPrefixFilter";
import { usePaginatedAdminTable } from "@/hooks/usePaginatedAdminTable";

const PAGE_SIZE = 20;

type Props = {
  tenants: AdminTenant[];
};

function formatDateTime(value: string) {
  return new Date(value).toLocaleString();
}

export default function InvitesTableCard({ tenants }: Props) {
  const [selectedTenantId, setSelectedTenantId] = useState("");

  const [filterEmail, setFilterEmail] = useState("");
  const [filterStatus, setFilterStatus] = useState<InviteStatusFilter>("");

  const emailFilter = useDebouncedPrefixFilter(filterEmail);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);

  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");

  const [cleanupLoading, setCleanupLoading] = useState(false);
  const [revokeLoadingId, setRevokeLoadingId] = useState<string | null>(null);

  const [tableSuccess, setTableSuccess] = useState<string | null>(null);
  const [invitesError, setInvitesError] = useState<string | null>(null);

  const queryKey = JSON.stringify({
    tenantId: selectedTenantId,
    page,
    pageSize,
    sort,
    direction,
    email: emailFilter.debounced,
    status: filterStatus,
  });

  const loader = useCallback(async (): Promise<SpringPage<InviteRow>> => {
    const empty: SpringPage<InviteRow> = {
      content: [],
      page: {
        number: page,
        size: pageSize,
        totalElements: 0,
        totalPages: 0,
      },
    };

    if (!selectedTenantId || emailFilter.shouldBlock()) {
      return empty;
    }

    const query = new URLSearchParams({
      page: String(page),
      size: String(pageSize),
      sort,
      direction,
    });

    if (emailFilter.debounced) query.set("email", emailFilter.debounced);
    if (filterStatus) query.set("status", filterStatus);

    try {
      const res = await apiFetch<SpringPage<InviteRow>>(
        `/api/tenants/${selectedTenantId}/invites?${query.toString()}`,
      );

      emailFilter.registerResult(res.content.length);

      return res;
    } catch (err) {
      if (err instanceof ApiError) setInvitesError(err.message);
      else setInvitesError("Failed to load invites.");
      return empty;
    }
  }, [
    selectedTenantId,
    page,
    pageSize,
    sort,
    direction,
    emailFilter,
    filterStatus,
  ]);

  const { data, loading, isPending, refetch } = usePaginatedAdminTable(loader, {
    enabled: Boolean(selectedTenantId),
    queryKey,
    resetKeys: [emailFilter.debounced, filterStatus],
  });

  const invites = useMemo(() => data?.content ?? [], [data?.content]);

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

  async function onExpireInvites() {
    if (!selectedTenantId) return;

    setCleanupLoading(true);
    setTableSuccess(null);

    try {
      const result = await apiFetch<{ expiredInvites: number }>(
        `/api/tenants/${selectedTenantId}/invites/expire`,
        { method: "POST" },
      );

      setTableSuccess(`Expired ${result.expiredInvites} pending invites.`);
      await refetch();
    } catch (err) {
      if (err instanceof ApiError) setInvitesError(err.message);
      else setInvitesError("Cleanup failed.");
    } finally {
      setCleanupLoading(false);
    }
  }

  async function onRevokeInvite(inviteId: string) {
    setRevokeLoadingId(inviteId);

    try {
      await apiFetch<void>(
        `/api/tenants/${selectedTenantId}/invites/${inviteId}/revoke`,
        { method: "POST" },
      );

      setTableSuccess("Invite revoked successfully.");
      setInvitesError(null);

      await refetch();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.message.includes("terminal invite")) {
          setInvitesError("Invite already processed. Refreshing...");
          await refetch();
          return;
        }
        setInvitesError(err.message);
      } else {
        setInvitesError("Failed to revoke invite.");
      }
    } finally {
      setRevokeLoadingId(null);
    }
  }

  function onResetFilters() {
    setFilterEmail("");
    setFilterStatus("");
    setPage(0);
    emailFilter.reset();
  }

  const columnWidths = useMemo(
    () => (
      <TableColumnWidths widths={["26%", "20%", "12%", "12%", "18%", "12%"]} />
    ),
    [],
  );

  const rows = invites.map((invite) => (
    <tr key={invite.id} className="border-b border-(--color-border)">
      <td className="px-4 py-2 text-sm">
        <div>{invite.email}</div>
        <div className="text-xs text-(--color-text-muted)">
          Created {formatDateTime(invite.createdAt)}
        </div>
      </td>

      <td className="px-4 py-2 text-sm">
        {invite.firstName} {invite.lastName}
      </td>

      <td className="px-4 py-2 text-sm">{invite.tenantRole}</td>

      <td className="px-4 py-2 text-sm">{invite.status}</td>

      <td className="px-4 py-2 text-sm">{formatDateTime(invite.expiresAt)}</td>

      <td className="px-4 py-2 text-right">
        <Button
          size="sm"
          variant="danger"
          onClick={() => onRevokeInvite(invite.id)}
          disabled={
            revokeLoadingId === invite.id || invite.status !== "PENDING"
          }
        >
          {revokeLoadingId === invite.id ? "Revoking…" : "Revoke"}
        </Button>
      </td>
    </tr>
  ));

  const filters = (
    <div className="flex flex-wrap items-end gap-3">
      <Select
        label="Tenant"
        value={selectedTenantId}
        className="w-56"
        onChange={(e) => {
          setSelectedTenantId(e.target.value);
          setPage(0);
        }}
      >
        <option value="">Select tenant</option>
        {tenants.map((t) => (
          <option key={t.id} value={t.id}>
            {t.name}
          </option>
        ))}
      </Select>

      <Select
        label="Status"
        value={filterStatus}
        className="w-56"
        onChange={(e) => setFilterStatus(e.target.value as InviteStatusFilter)}
      >
        <option value="">All statuses</option>
        <option value="PENDING">Pending</option>
        <option value="ACCEPTED">Accepted</option>
      </Select>

      <Input
        label="Email"
        type="search"
        placeholder="Search by email"
        value={filterEmail}
        onChange={(e) => {
          setFilterEmail(e.target.value);
          setPage(0);
        }}
        className="w-56"
      />

      <Button type="button" variant="outline" onClick={onResetFilters}>
        Reset
      </Button>
    </div>
  );

  const actions = (
    <Button
      onClick={onExpireInvites}
      disabled={!selectedTenantId}
      variant="secondary"
    >
      {cleanupLoading ? "Expiring…" : "Expire Pending Invites"}
    </Button>
  );

  const tableHeader = useMemo(
    () => (
      <thead className="sticky top-0 bg-(--color-table-header)">
        <tr>
          <SortableHeader
            label="Email"
            field="email"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Name"
            field="firstName"
            activeSort={sort}
            direction={direction}
            onSortChange={handleSort}
          />
          <SortableHeader
            label="Role"
            field="tenantRole"
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
            label="Expires"
            field="expiresAt"
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

  return (
    <div className="flex flex-col gap-6">
      <Card title="Filters">
        <TableToolbar filters={filters} actions={actions} />
      </Card>

      <Card
        title={
          selectedTenantId
            ? `Invites (${tenants.find((t) => t.id === selectedTenantId)?.name ?? "Tenant"})`
            : "Invites"
        }
      >
        {selectedTenantId ? (
          <DataTable
            loading={loading && invites.length === 0}
            empty={!loading && invites.length === 0}
            emptyMessage="No invites found"
            className={
              (loading || isPending) && invites.length > 0
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
        ) : (
          <p className="text-sm text-(--color-text-muted)">
            Select a tenant to view invites.
          </p>
        )}

        {tableSuccess && (
          <p className="mt-4 text-sm text-(--color-success)">{tableSuccess}</p>
        )}

        {invitesError && (
          <p className="mt-4 text-sm text-(--color-error)">{invitesError}</p>
        )}
      </Card>
    </div>
  );
}