"use client";

import { memo, useState } from "react";
import { useQueryClient, useMutation } from "@tanstack/react-query";

import type {
  MembershipStatus,
  TenantRole,
  TenantUser,
} from "@/types/admin/TenantUser";

import RowActionMenu from "@/components/ui/RowActionMenu";
import ConfirmDialog from "@/components/ui/ConfirmDialog";
import Select from "@/components/ui/Select";
import { changeUserRole, changeUserStatus } from "@/lib/admin/adminTenantUsers";
import { ROLE_OPTIONS_MUTATION } from "@/lib/admin/tenantUserOptions";

type Props = {
  tenantId: string;
  user: TenantUser;
};

function UserRowComponent({ tenantId, user }: Props) {
  const queryClient = useQueryClient();

  const [roleOpen, setRoleOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);

  const [nextRole, setNextRole] = useState<TenantRole>(user.role);
  const [nextStatus, setNextStatus] = useState(user.status);

  const [roleComment, setRoleComment] = useState("");
  const [statusComment, setStatusComment] = useState("");

  const invalidateUsers = async () => {
    await queryClient.invalidateQueries({
      queryKey: ["tenantUsers"],
    });
  };

  const roleMutation = useMutation({
    mutationFn: async (payload: { role: TenantRole; comment: string }) => {
      await changeUserRole(
        tenantId,
        user.userId,
        payload.role,
        payload.comment,
      );
    },
    onSuccess: async () => {
      setRoleOpen(false);
      setRoleComment("");
      await invalidateUsers();
    },
  });

  const statusMutation = useMutation({
    mutationFn: async (payload: {
      status: MembershipStatus;
      comment: string;
    }) => {
      await changeUserStatus(
        tenantId,
        user.userId,
        payload.status,
        payload.comment,
      );
    },
    onSuccess: async () => {
      setStatusOpen(false);
      setStatusComment("");
      await invalidateUsers();
    },
  });

  const loading = roleMutation.isPending || statusMutation.isPending;
  const nextStatusActionLabel =
    user.status === "ACTIVE" ? "Suspend" : "Activate";

  return (
    <>
      <tr className="border-b border-(--color-table-border) align-top hover:bg-(--color-table-row-hover)">
        <td className="px-4 py-3 text-sm text-(--color-text-primary)">
          <div className="flex min-w-0 flex-col">
            <span className="truncate">{user.displayName}</span>
            <span className="truncate text-xs text-(--color-text-muted)">
              {user.email}
            </span>
          </div>
        </td>

        <td className="truncate px-4 py-3 text-sm text-(--color-text-primary)">
          {user.jobTitle || "-"}
        </td>
        <td className="truncate px-4 py-3 text-sm text-(--color-text-primary)">
          {user.department || "-"}
        </td>
        <td className="px-4 py-3 text-sm text-(--color-text-primary)">
          {user.role}
        </td>
        <td className="px-4 py-3 text-sm text-(--color-text-primary)">
          {user.status}
        </td>
        <td className="truncate px-4 py-3 text-sm text-(--color-text-primary)">
          {new Date(user.createdAt).toLocaleDateString()}
        </td>
        <td className="truncate px-4 py-3 text-sm text-(--color-text-primary)">
          {new Date(user.updatedAt).toLocaleDateString()}
        </td>

        <td className="px-4 py-3 text-right">
          <RowActionMenu>
            {({ close }) => (
              <>
                <button
                  type="button"
                  onClick={() => {
                    close();
                    setNextRole(user.role);
                    setRoleComment("");
                    setRoleOpen(true);
                  }}
                  className="block w-full cursor-pointer px-3 py-2 text-left text-sm hover:bg-(--color-surface-alt)"
                >
                  Change role
                </button>

                <button
                  type="button"
                  onClick={() => {
                    close();
                    setNextStatus(
                      user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE",
                    );
                    setStatusComment("");
                    setStatusOpen(true);
                  }}
                  className="block w-full cursor-pointer px-3 py-2 text-left text-sm hover:bg-(--color-surface-alt)"
                >
                  {nextStatusActionLabel}
                </button>
              </>
            )}
          </RowActionMenu>
        </td>
      </tr>

      <ConfirmDialog
        open={roleOpen}
        title="Change user role"
        description={`Change role for "${user.displayName}"`}
        confirmLabel="Update role"
        requireComment
        comment={roleComment}
        onCommentChange={setRoleComment}
        loading={loading}
        confirmDisabled={nextRole === user.role}
        onConfirm={() =>
          roleMutation.mutateAsync({
            role: nextRole,
            comment: roleComment,
          })
        }
        onClose={() => {
          setRoleOpen(false);
          setRoleComment("");
        }}
      >
        <div className="flex flex-col gap-1">
          <label className="text-xs text-(--color-text-muted)">New role</label>
          <Select
            value={nextRole}
            onChange={(e) => setNextRole(e.target.value as TenantRole)}
          >
            {ROLE_OPTIONS_MUTATION.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </Select>
        </div>
      </ConfirmDialog>

      <ConfirmDialog
        open={statusOpen}
        title="Change user status"
        description={`Update status for "${user.displayName}"`}
        confirmLabel="Update status"
        requireComment
        comment={statusComment}
        onCommentChange={setStatusComment}
        loading={loading}
        confirmDisabled={nextStatus === user.status}
        onConfirm={() =>
          statusMutation.mutateAsync({
            status: nextStatus,
            comment: statusComment,
          })
        }
        onClose={() => {
          setStatusOpen(false);
          setStatusComment("");
        }}
      />
    </>
  );
}

export const UserRow = memo(UserRowComponent);
