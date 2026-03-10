"use client";

import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import type { AdminTenant } from "@/types/admin/Tenant";
import { InviteRow, InviteStatusFilter } from "@/types/invites/types";

type InvitesTableCardProps = {
  tenants: AdminTenant[];
  selectedTenantId: string;
  onSelectedTenantIdChange: (value: string) => void;

  filterEmail: string;
  onFilterEmailChange: (value: string) => void;

  filterStatus: InviteStatusFilter;
  onFilterStatusChange: (value: InviteStatusFilter) => void;

  onFilterSubmit: (e: React.FormEvent<HTMLFormElement>) => void;
  onResetFilters: () => void;

  onCleanupInvites: () => void;

  invites: InviteRow[];
  invitesLoading: boolean;
  cleanupLoading: boolean;

  revokeLoadingId: string | null;

  tableSuccess: string | null;
  invitesError: string | null;

  invitePage: number;
  inviteTotalPages: number;
  inviteTotalElements: number;

  onPreviousPage: () => void;
  onNextPage: () => void;

  onRevokeInvite: (inviteId: string) => void;
};

function formatDateTime(value: string) {
  return new Date(value).toLocaleString();
}

export default function InvitesTableCard({
  tenants,
  selectedTenantId,
  onSelectedTenantIdChange,

  filterEmail,
  onFilterEmailChange,
  filterStatus,
  onFilterStatusChange,

  onFilterSubmit,
  onResetFilters,
  onCleanupInvites,

  invites,
  invitesLoading,
  cleanupLoading,
  revokeLoadingId,

  tableSuccess,
  invitesError,

  invitePage,
  inviteTotalPages,
  inviteTotalElements,

  onPreviousPage,
  onNextPage,
  onRevokeInvite,
}: InvitesTableCardProps) {
  return (
    <Card>
      <div className="mb-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-lg font-semibold text-(--color-text-primary)">
            Existing invites
          </h2>

          <p className="mt-1 text-sm text-(--color-text-secondary)">
            Search invites by email or status, revoke active invites, and run
            cleanup for expired invites.
          </p>
        </div>

        <button
          type="button"
          onClick={onCleanupInvites}
          disabled={cleanupLoading || !selectedTenantId}
          className="rounded-md bg-(--color-surface-alt) px-4 py-2 text-(--color-text-primary) hover:opacity-90 disabled:opacity-50"
        >
          {cleanupLoading ? "Cleaning…" : "Cleanup Expired Invites"}
        </button>
      </div>

      <div className="mb-4">
        <Select
          value={selectedTenantId}
          onChange={(e) => onSelectedTenantIdChange(e.target.value)}
        >
          <option value="">Select a tenant</option>

          {tenants?.map((tenant) => (
            <option key={tenant.id} value={tenant.id}>
              {tenant.name}
            </option>
          ))}
        </Select>
      </div>

      <form
        onSubmit={onFilterSubmit}
        className="mb-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_12rem] lg:grid-cols-[minmax(0,1fr)_12rem_auto_auto]"
      >
        <Input
          type="search"
          placeholder="Search by email"
          value={filterEmail}
          onChange={(e) => onFilterEmailChange(e.target.value)}
          disabled={!selectedTenantId}
        />

        <Select
          value={filterStatus}
          onChange={(e) =>
            onFilterStatusChange(e.target.value as InviteStatusFilter)
          }
          disabled={!selectedTenantId}
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="ACCEPTED">Accepted</option>
        </Select>

        <button
          type="submit"
          disabled={!selectedTenantId}
          className="rounded-md bg-(--color-primary) px-4 py-2 text-(--color-text-inverse) hover:opacity-90 disabled:opacity-50"
        >
          Search
        </button>

        <button
          type="button"
          onClick={onResetFilters}
          disabled={!selectedTenantId}
          className="rounded-md bg-(--color-surface-alt) px-4 py-2 text-(--color-text-primary) hover:opacity-90 disabled:opacity-50"
        >
          Reset
        </button>
      </form>

      {tableSuccess && (
        <p className="mb-4 text-sm text-(--color-success)">{tableSuccess}</p>
      )}

      {invitesError && (
        <p className="mb-4 text-sm text-(--color-error)">{invitesError}</p>
      )}

      {!selectedTenantId && (
        <p className="mb-4 text-sm text-(--color-text-secondary)">
          Select a tenant first.
        </p>
      )}

      <div className="overflow-x-auto rounded-lg border border-(--color-border) bg-(--color-surface)">
        <table className="min-w-full">
          <thead className="bg-(--color-surface-alt) text-(--color-text-secondary)">
            <tr>
              <th className="px-4 py-2 text-left text-xs uppercase">Email</th>
              <th className="px-4 py-2 text-left text-xs uppercase">Name</th>
              <th className="px-4 py-2 text-left text-xs uppercase">Role</th>
              <th className="px-4 py-2 text-left text-xs uppercase">Status</th>
              <th className="px-4 py-2 text-left text-xs uppercase">Expires</th>
              <th className="px-4 py-2 text-left text-xs uppercase">Actions</th>
            </tr>
          </thead>

          <tbody>
            {invitesLoading ? (
              <tr className="border-b border-(--color-border)">
                <td
                  colSpan={6}
                  className="px-4 py-6 text-sm text-(--color-text-secondary)"
                >
                  Loading invites…
                </td>
              </tr>
            ) : invites.length === 0 ? (
              <tr className="border-b border-(--color-border)">
                <td
                  colSpan={6}
                  className="px-4 py-6 text-sm text-(--color-text-secondary)"
                >
                  No invites found.
                </td>
              </tr>
            ) : (
              invites.map((invite) => (
                <tr
                  key={invite.id}
                  className="border-b border-(--color-border) hover:bg-(--color-surface-alt)"
                >
                  <td className="px-4 py-2 text-sm text-(--color-text-primary)">
                    <div>{invite.email}</div>
                    <div className="text-xs text-(--color-text-muted)">
                      Created {formatDateTime(invite.createdAt)}
                    </div>
                  </td>

                  <td className="px-4 py-2 text-sm text-(--color-text-primary)">
                    <div>
                      {invite.firstName} {invite.lastName}
                    </div>

                    {(invite.jobTitle || invite.department) && (
                      <div className="text-xs text-(--color-text-muted)">
                        {[invite.jobTitle, invite.department]
                          .filter(Boolean)
                          .join(" · ")}
                      </div>
                    )}
                  </td>

                  <td className="px-4 py-2 text-sm text-(--color-text-primary)">
                    {invite.tenantRole}
                  </td>

                  <td className="px-4 py-2 text-sm text-(--color-text-primary)">
                    {invite.status}
                  </td>

                  <td className="px-4 py-2 text-sm text-(--color-text-primary)">
                    {formatDateTime(invite.expiresAt)}
                  </td>

                  <td className="px-4 py-2 text-sm">
                    <button
                      type="button"
                      onClick={() => onRevokeInvite(invite.id)}
                      disabled={
                        revokeLoadingId === invite.id ||
                        invite.status !== "PENDING"
                      }
                      className="rounded-md bg-(--color-error) px-3 py-1.5 text-(--color-text-inverse) hover:opacity-90 disabled:opacity-50"
                    >
                      {revokeLoadingId === invite.id ? "Revoking…" : "Revoke"}
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="mt-4 flex flex-col gap-3 text-sm text-(--color-text-secondary) sm:flex-row sm:items-center sm:justify-between">
        <span>
          {inviteTotalElements > 0
            ? `Page ${invitePage + 1} of ${Math.max(
                inviteTotalPages,
                1
              )} · ${inviteTotalElements} invite(s)`
            : "0 invites"}
        </span>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onPreviousPage}
            disabled={invitePage === 0 || invitesLoading}
            className="rounded-md bg-(--color-surface-alt) px-3 py-1.5 text-(--color-text-primary) hover:opacity-90 disabled:opacity-50"
          >
            Previous
          </button>

          <button
            type="button"
            onClick={onNextPage}
            disabled={
              invitesLoading ||
              inviteTotalPages === 0 ||
              invitePage >= inviteTotalPages - 1
            }
            className="rounded-md bg-(--color-surface-alt) px-3 py-1.5 text-(--color-text-primary) hover:opacity-90 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      </div>
    </Card>
  );
}