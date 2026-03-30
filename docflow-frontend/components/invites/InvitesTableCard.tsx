"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import type { AdminTenant } from "@/types/admin/Tenant";
import {
  STATUS_LABELS,
  STATUS_OPTIONS,
  type InviteStatusFilter,
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
import { useInvitesQuery } from "@/hooks/invites/useInvitesQuery";
import { useInviteMutations } from "./useInviteMutations";

const PAGE_SIZE = 20;

type Props = {
  tenants: AdminTenant[];
};

type TableStatus =
  | { type: "success"; message: string }
  | { type: "error"; message: string }
  | null;

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

  const [status, setStatus] = useState<TableStatus>(null);

  const { invites, pageInfo, isLoading, isFetching, error } = useInvitesQuery({
    tenantId: selectedTenantId,
    page,
    pageSize,
    sort,
    direction,
    email: emailFilter.debounced,
    status: filterStatus,
    enabled: Boolean(selectedTenantId) && !emailFilter.shouldBlock(),
  });

  useEffect(() => {
    emailFilter.registerResult(invites.length);
  }, [emailFilter, invites.length]);

  const { expireMutation, revokeMutation } = useInviteMutations({
    tenantId: selectedTenantId,
    setStatus,
  });

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

  function onExpireInvites() {
    if (!selectedTenantId) return;
    setStatus(null);
    expireMutation.mutate();
  }

  function onRevokeInvite(inviteId: string) {
    setStatus(null);
    revokeMutation.mutate(inviteId);
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

      <td className="px-4 py-2 text-sm">{STATUS_LABELS[invite.status]}</td>

      <td className="px-4 py-2 text-sm">{formatDateTime(invite.expiresAt)}</td>

      <td className="px-4 py-2 text-right">
        <Button
          size="sm"
          variant="danger"
          onClick={() => onRevokeInvite(invite.id)}
          disabled={
            (revokeMutation.isPending &&
              revokeMutation.variables === invite.id) ||
            invite.status !== "PENDING"
          }
        >
          {revokeMutation.isPending && revokeMutation.variables === invite.id
            ? "Revoking…"
            : "Revoke"}
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
        onChange={(e) => {
          const value = e.target.value;
          if (value === "" || value in STATUS_LABELS) {
            setFilterStatus(value as InviteStatusFilter);
          }
        }}
      >
        {STATUS_OPTIONS.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
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
      disabled={!selectedTenantId || expireMutation.isPending}
      variant="secondary"
    >
      {expireMutation.isPending ? "Expiring…" : "Expire Pending Invites"}
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
            loading={isLoading && invites.length === 0}
            empty={!isLoading && invites.length === 0}
            emptyMessage="No invites found"
            className={
              (isLoading || isFetching) && invites.length > 0
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
                  onPageSizeChange={(size: number) => {
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

        {status?.type === "success" && (
          <p className="mt-4 text-sm text-(--color-success)">
            {status.message}
          </p>
        )}

        {status?.type === "error" && (
          <p className="mt-4 text-sm text-(--color-error)">{status.message}</p>
        )}

        {error && (
          <p className="mt-4 text-sm text-(--color-error)">
            {(error as Error).message || "Failed to load invites"}
          </p>
        )}
      </Card>
    </div>
  );
}
