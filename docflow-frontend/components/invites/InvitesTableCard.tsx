"use client";

import { useEffect, useMemo, useState } from "react";

import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

import type { AdminTenant } from "@/types/admin/Tenant";
import {
  InvitePage,
  InviteRow,
  InviteStatusFilter,
} from "@/types/invites/types";

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

  const [invites, setInvites] = useState<InviteRow[]>([]);

  const [invitePage, setInvitePage] = useState(0);
  const [inviteTotalPages, setInviteTotalPages] = useState(0);
  const [inviteTotalElements, setInviteTotalElements] = useState(0);

  const [invitesLoading, setInvitesLoading] = useState(false);
  const [cleanupLoading, setCleanupLoading] = useState(false);
  const [revokeLoadingId, setRevokeLoadingId] = useState<string | null>(null);

  const [tableSuccess, setTableSuccess] = useState<string | null>(null);
  const [invitesError, setInvitesError] = useState<string | null>(null);

  const [sort, setSort] = useState("createdAt");
  const [direction, setDirection] = useState<"ASC" | "DESC">("DESC");

  useEffect(() => {
    if (!selectedTenantId) return;

    void loadInvites();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    selectedTenantId,
    invitePage,
    emailFilter.debounced,
    filterStatus,
    sort,
    direction,
  ]);

  async function loadInvites() {
    if (emailFilter.shouldBlock()) {
      setInvites([]);
      setInviteTotalPages(0);
      setInviteTotalElements(0);
      return;
    }

    setInvitesLoading(true);
    setInvitesError(null);

    try {
      const query = new URLSearchParams({
        page: String(invitePage),
        size: String(PAGE_SIZE),
        sort,
        direction,
      });

      if (emailFilter.debounced) query.set("email", emailFilter.debounced);
      if (filterStatus) query.set("status", filterStatus);

      const res = await apiFetch<InvitePage>(
        `/api/tenants/${selectedTenantId}/invites?${query.toString()}`,
      );

      setInvites(res.content);
      setInviteTotalPages(res.totalPages);
      setInviteTotalElements(res.totalElements);

      emailFilter.registerResult(res.totalElements);
    } catch (err) {
      if (err instanceof ApiError) setInvitesError(err.message);
      else setInvitesError("Failed to load invites.");
    } finally {
      setInvitesLoading(false);
    }
  }

  function handleSort(field: string) {
    setSort((prev) => {
      if (prev === field) {
        setDirection((d) => (d === "ASC" ? "DESC" : "ASC"));
        return prev;
      }

      setDirection("ASC");
      return field;
    });

    setInvitePage(0);
  }

  async function onCleanupInvites() {
    if (!selectedTenantId) return;

    setCleanupLoading(true);
    setTableSuccess(null);

    try {
      const result = await apiFetch<{
        deletedInvites: number;
        deletedUsers: number;
      }>(`/api/tenants/${selectedTenantId}/invites/cleanup`, {
        method: "POST",
      });

      setTableSuccess(
        `Cleanup completed. Deleted ${result.deletedInvites} expired invites and ${result.deletedUsers} orphaned users.`,
      );

      await loadInvites();
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

      await loadInvites();
    } catch (err) {
      if (err instanceof ApiError) setInvitesError(err.message);
      else setInvitesError("Failed to revoke invite.");
    } finally {
      setRevokeLoadingId(null);
    }
  }

  function onResetFilters() {
    setFilterEmail("");
    setFilterStatus("");
    setInvitePage(0);
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
      <div className="w-56">
        <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
          <span>Tenant</span>

          <Select
            value={selectedTenantId}
            size={6}
            className="max-h-40 overflow-y-auto"
            onChange={(e) => {
              setSelectedTenantId(e.target.value);
              setInvitePage(0);
            }}
          >
            <option value="">Select tenant</option>

            {tenants.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </Select>
        </label>
      </div>

      <div className="w-56">
        <label className="flex flex-col gap-1 text-xs text-(--color-text-muted)">
          <span>Status</span>
          <Select
            value={filterStatus}
            onChange={(e) =>
              setFilterStatus(e.target.value as InviteStatusFilter)
            }
          >
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="ACCEPTED">Accepted</option>
          </Select>
        </label>
      </div>

      <Input
        label="Email"
        type="search"
        placeholder="Search by email"
        value={filterEmail}
        onChange={(e) => {
          setFilterEmail(e.target.value);
          setInvitePage(0);
        }}
        className="w-56"
      />

      <Button type="button" variant="ghost" onClick={onResetFilters}>
        Reset
      </Button>
    </div>
  );

  const actions = (
    <Button
      onClick={onCleanupInvites}
      disabled={!selectedTenantId}
      variant="secondary"
    >
      {cleanupLoading ? "Cleaning…" : "Cleanup Expired Invites"}
    </Button>
  );

  return (
    <div className="flex flex-col gap-6">
      <Card title="Filters">
        <TableToolbar filters={filters} actions={actions} />
      </Card>

      <Card title="Existing invites">
        <DataTable
          loading={invitesLoading && invites.length === 0}
          empty={!invitesLoading && invites.length === 0}
          emptyMessage="No invites found"
          footer={
            <DataTableFooter
              page={invitePage}
              pageSize={PAGE_SIZE}
              totalPages={inviteTotalPages}
              totalElements={inviteTotalElements}
              onPageChange={(p) => setInvitePage(p)}
            />
          }
        >
          {columnWidths}

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

          <tbody>{rows}</tbody>
        </DataTable>

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