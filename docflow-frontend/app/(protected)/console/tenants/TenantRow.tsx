"use client";

import { memo, useState } from "react";
import type { AdminTenant } from "@/types/admin/Tenant";

import RowActionMenu from "@/components/ui/RowActionMenu";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import EditTenantDialog from "./EditTenantDialog";

import {
  suspendTenant,
  reactivateTenant,
  terminateTenant,
} from "@/lib/admin/adminTenants";

type Props = {
  tenant: AdminTenant;
  onUpdated: () => void;
};

function TenantRowComponent({ tenant, onUpdated }: Props) {
  const [editOpen, setEditOpen] = useState(false);

  const [suspendOpen, setSuspendOpen] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);
  const [terminateOpen, setTerminateOpen] = useState(false);

  const [comment, setComment] = useState("");
  const [loading, setLoading] = useState(false);

  async function confirmSuspend() {
    setLoading(true);
    try {
      await suspendTenant(tenant.id, comment);
      setSuspendOpen(false);
      setComment("");
      onUpdated();
    } finally {
      setLoading(false);
    }
  }

  async function confirmReactivate() {
    setLoading(true);
    try {
      await reactivateTenant(tenant.id, comment);
      setReactivateOpen(false);
      setComment("");
      onUpdated();
    } finally {
      setLoading(false);
    }
  }

  async function confirmTerminate() {
    setLoading(true);
    try {
      await terminateTenant(tenant.id, comment);
      setTerminateOpen(false);
      setComment("");
      onUpdated();
    } finally {
      setLoading(false);
    }
  }

  const statusBadge =
    tenant.status === "ACTIVE"
      ? "bg-(--color-success) text-(--color-text-inverse)"
      : tenant.status === "SUSPENDED"
      ? "bg-(--color-warning) text-(--color-text-inverse)"
      : "bg-(--color-error) text-(--color-text-inverse)";

  return (
    <>
      <tr className="border-b border-(--color-table-border) hover:bg-(--color-table-row-hover) h-13 text-sm">
        <td className="px-4 font-medium text-(--color-text-primary)">
          {tenant.name}
        </td>

        <td className="px-4">
          <span
            className={`inline-flex items-center h-5 px-2 text-xs rounded-md ${statusBadge}`}
          >
            {tenant.status}
          </span>
        </td>

        <td className="px-4 text-(--color-text-primary)">
          {tenant.managerName ?? "—"}
        </td>

        <td className="px-4 text-(--color-text-primary)">
          {tenant.membersCount}
        </td>

        <td className="px-4 text-(--color-text-primary)">
          <span className="inline-flex items-center h-5 px-2 text-xs rounded-md bg-(--color-surface-alt)">
            {tenant.dataRegion ?? "Default"}
          </span>
        </td>

        <td className="px-4 text-(--color-text-primary)">
          {tenant.retentionDays != null
            ? `${tenant.retentionDays} days`
            : "Default"}
        </td>

        <td className="px-4 text-(--color-text-secondary)">
          {tenant.lastActivity
            ? new Date(tenant.lastActivity).toLocaleDateString()
            : "—"}
        </td>

        <td className="px-4 text-right">
          <RowActionMenu>
            {({ close }) => (
              <>
                <button
                  type="button"
                  onClick={() => {
                    close();
                    setEditOpen(true);
                  }}
                  className="block w-full text-left px-3 py-2 text-sm hover:bg-(--color-surface-alt) cursor-pointer disabled:pointer-events-none"
                >
                  Update
                </button>

                {tenant.status === "ACTIVE" && (
                  <button
                    type="button"
                    onClick={() => {
                      close();
                      setSuspendOpen(true);
                    }}
                    className="block w-full text-left px-3 py-2 text-sm text-(--color-warning) hover:bg-(--color-surface-alt) cursor-pointer disabled:pointer-events-none"
                  >
                    Suspend
                  </button>
                )}

                {tenant.status === "SUSPENDED" && (
                  <button
                    type="button"
                    onClick={() => {
                      close();
                      setReactivateOpen(true);
                    }}
                    className="block w-full text-left px-3 py-2 text-sm hover:bg-(--color-surface-alt) cursor-pointer disabled:pointer-events-none"
                  >
                    Reactivate
                  </button>
                )}

                {tenant.status !== "TERMINATED" && (
                  <button
                    type="button"
                    onClick={() => {
                      close();
                      setTerminateOpen(true);
                    }}
                    className="block w-full text-left px-3 py-2 text-sm text-(--color-error) hover:bg-(--color-surface-alt) cursor-pointer disabled:pointer-events-none"
                  >
                    Terminate
                  </button>
                )}
              </>
            )}
          </RowActionMenu>
        </td>
      </tr>

      <EditTenantDialog
        tenant={tenant}
        open={editOpen}
        onClose={() => setEditOpen(false)}
        onSaved={onUpdated}
      />

      <ConfirmDialog
        open={suspendOpen}
        title="Suspend tenant"
        description={`Suspend "${tenant.name}"?`}
        confirmLabel="Suspend"
        requireComment
        comment={comment}
        onCommentChange={setComment}
        loading={loading}
        onConfirm={confirmSuspend}
        onClose={() => {
          setSuspendOpen(false);
          setComment("");
        }}
      />

      <ConfirmDialog
        open={reactivateOpen}
        title="Reactivate tenant"
        description={`Reactivate "${tenant.name}"?`}
        confirmLabel="Reactivate"
        requireComment
        comment={comment}
        onCommentChange={setComment}
        loading={loading}
        onConfirm={confirmReactivate}
        onClose={() => {
          setReactivateOpen(false);
          setComment("");
        }}
      />

      <ConfirmDialog
        open={terminateOpen}
        title="Terminate tenant"
        description={`Terminate "${tenant.name}"? This permanently disables access but keeps audit evidence.`}
        confirmLabel="Terminate"
        requireComment
        comment={comment}
        onCommentChange={setComment}
        loading={loading}
        onConfirm={confirmTerminate}
        onClose={() => {
          setTerminateOpen(false);
          setComment("");
        }}
      />
    </>
  );
}

export const TenantRow = memo(TenantRowComponent);