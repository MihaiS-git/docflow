"use client";

import { memo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";

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
};

function TenantRowComponent({ tenant }: Props) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [editOpen, setEditOpen] = useState(false);

  const [suspendOpen, setSuspendOpen] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);
  const [terminateOpen, setTerminateOpen] = useState(false);

  const [comment, setComment] = useState("");

  const invalidateTenants = async () => {
    await queryClient.invalidateQueries({ queryKey: ["tenants"] });
  };

  const suspendMutation = useMutation({
    mutationFn: async (nextComment: string) => {
      await suspendTenant(tenant.id, nextComment);
    },
    onSuccess: async () => {
      setSuspendOpen(false);
      setComment("");
      await invalidateTenants();
    },
  });

  const reactivateMutation = useMutation({
    mutationFn: async (nextComment: string) => {
      await reactivateTenant(tenant.id, nextComment);
    },
    onSuccess: async () => {
      setReactivateOpen(false);
      setComment("");
      await invalidateTenants();
    },
  });

  const terminateMutation = useMutation({
    mutationFn: async (nextComment: string) => {
      await terminateTenant(tenant.id, nextComment);
    },
    onSuccess: async () => {
      setTerminateOpen(false);
      setComment("");
      await invalidateTenants();
    },
  });

  async function confirmSuspend() {
    await suspendMutation.mutateAsync(comment);
  }

  async function confirmReactivate() {
    await reactivateMutation.mutateAsync(comment);
  }

  async function confirmTerminate() {
    await terminateMutation.mutateAsync(comment);
  }

  const loading =
    suspendMutation.isPending ||
    reactivateMutation.isPending ||
    terminateMutation.isPending;

  const statusBadge =
    tenant.status === "ACTIVE"
      ? "bg-(--color-success) text-(--color-text-inverse)"
      : tenant.status === "SUSPENDED"
        ? "bg-(--color-warning) text-(--color-text-inverse)"
        : "bg-(--color-error) text-(--color-text-inverse)";

  return (
    <>
      <tr className="h-13 border-b border-(--color-table-border) text-sm hover:bg-(--color-table-row-hover)">
        <td
          className="px-4 text-left font-medium text-(--color-text-primary) hover:cursor-pointer hover:underline"
          onClick={() => router.push(`/console/tenants/${tenant.id}`)}
        >
          {tenant.name}
        </td>

        <td className="px-4">
          <span
            className={`inline-flex h-5 items-center rounded-md px-2 text-xs ${statusBadge}`}
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
          <span className="inline-flex h-5 items-center rounded-md bg-(--color-surface-alt) px-2 text-xs">
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
                  className="block w-full cursor-pointer px-3 py-2 text-left text-sm hover:bg-(--color-surface-alt) disabled:pointer-events-none"
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
                    className="block w-full cursor-pointer px-3 py-2 text-left text-sm text-(--color-warning) hover:bg-(--color-surface-alt) disabled:pointer-events-none"
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
                    className="block w-full cursor-pointer px-3 py-2 text-left text-sm hover:bg-(--color-surface-alt) disabled:pointer-events-none"
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
                    className="block w-full cursor-pointer px-3 py-2 text-left text-sm text-(--color-error) hover:bg-(--color-surface-alt) disabled:pointer-events-none"
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