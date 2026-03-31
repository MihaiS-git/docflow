"use client";

import { memo, useMemo, useState } from "react";
import { X } from "lucide-react";
import { toast } from "sonner";

import { ForbiddenError } from "@/lib/apiErrors";
import { useUserMutations } from "@/hooks/admin/useUserMutations";

import type { AdminUser } from "@/types/admin/AdminUser";

import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import RowActionMenu from "@/components/ui/RowActionMenu";

type Props = {
  user: AdminUser;
  allRoles: string[];
};

function AdminUserRowComponent({ user, allRoles }: Props) {
  const { activate, lock, disable, assign, revoke } = useUserMutations();

  const [selectedRole, setSelectedRole] = useState("");
  const [revokingRole, setRevokingRole] = useState<string | null>(null);

  const userRoles = useMemo(() => user.roles ?? [], [user.roles]);

  const availableRoles = useMemo(
    () => allRoles.filter((role) => !userRoles.includes(role)),
    [allRoles, userRoles],
  );

  async function onGrant() {
    if (!selectedRole || assign.isPending) return;

    await assign.mutateAsync({
      userId: user.id,
      role: selectedRole,
    });

    setSelectedRole("");
  }

  async function onActivate() {
    if (activate.isPending || user.status === "ACTIVE") return;
    await activate.mutateAsync(user.id);
  }

  async function onLock() {
    if (lock.isPending || user.status === "LOCKED") return;

    try {
      await lock.mutateAsync(user.id);
    } catch (err) {
      if (
        err instanceof ForbiddenError &&
        err.errorCode === "SELF_ACTION_FORBIDDEN"
      ) {
        toast.error("You cannot lock your own account");
        return;
      }
      throw err;
    }
  }

  async function onDisable() {
    if (disable.isPending || user.status === "DISABLED") return;

    try {
      await disable.mutateAsync(user.id);
    } catch (err) {
      if (
        err instanceof ForbiddenError &&
        err.errorCode === "SELF_ACTION_FORBIDDEN"
      ) {
        toast.error("You cannot disable your own account");
        return;
      }
      throw err;
    }
  }

  async function onRevoke(role: string) {
    if (revokingRole === role || revoke.isPending) return;

    setRevokingRole(role);
    try {
      await revoke.mutateAsync({
        userId: user.id,
        role,
      });
    } finally {
      setRevokingRole(null);
    }
  }

  const statusBadge =
    user.status === "ACTIVE"
      ? "bg-(--color-success) text-(--color-text-inverse)"
      : user.status === "LOCKED"
        ? "bg-(--color-warning) text-(--color-text-inverse)"
        : "bg-(--color-error) text-(--color-text-inverse)";

  return (
    <tr className="border-b border-(--color-table-border) align-top hover:bg-(--color-table-row-hover)">
      <td className="px-4 py-3 text-sm text-(--color-text-primary)">
        {user.email}
      </td>

      <td className="px-4 py-3 text-sm text-(--color-text-primary)">
        <span
          className={`inline-flex h-5 items-center gap-1 rounded-md px-2 text-xs leading-none ${statusBadge}`}
        >
          {user.status}
        </span>
      </td>

      <td className="px-4 py-3 text-sm text-(--color-text-primary)">
        <div className="flex flex-wrap gap-2">
          {userRoles.length === 0 ? (
            <span className="text-sm text-(--color-text-muted)">
              No roles loaded
            </span>
          ) : (
            userRoles.map((role) => (
              <span
                key={role}
                className="inline-flex h-5 items-center gap-1 rounded-md bg-(--color-surface-alt) px-2 text-xs leading-none"
              >
                {role}

                <button
                  type="button"
                  disabled={revokingRole === role || revoke.isPending}
                  onClick={() => onRevoke(role)}
                  className="ml-1 flex items-center justify-center text-(--color-error) hover:opacity-80 disabled:opacity-40 cursor-pointer disabled:pointer-events-none"
                  aria-label={`Revoke ${role}`}
                >
                  <X size={12} strokeWidth={2.5} />
                </button>
              </span>
            ))
          )}
        </div>

        <div className="mt-2 flex items-center gap-2">
          <Select
            value={selectedRole}
            onChange={(e) => setSelectedRole(e.target.value)}
            disabled={availableRoles.length === 0 || assign.isPending}
          >
            <option value="">Add role…</option>

            {availableRoles.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </Select>

          <Button
            size="sm"
            disabled={!selectedRole || assign.isPending}
            onClick={onGrant}
          >
            Add
          </Button>
        </div>
      </td>

      <td className="px-4 py-3 text-right">
        <RowActionMenu>
          {({ close }) => (
            <>
              <button
                type="button"
                onClick={async () => {
                  await onActivate();
                  close();
                }}
                disabled={user.status === "ACTIVE" || activate.isPending}
                className="block w-full px-3 py-2 text-left text-sm hover:bg-(--color-surface-alt) disabled:opacity-50 cursor-pointer disabled:pointer-events-none"
              >
                Activate
              </button>

              <button
                type="button"
                onClick={async () => {
                  await onLock();
                  close();
                }}
                disabled={user.status === "LOCKED" || lock.isPending}
                className="block w-full px-3 py-2 text-left text-sm hover:bg-(--color-surface-alt) disabled:opacity-50 cursor-pointer disabled:pointer-events-none"
              >
                Lock
              </button>

              <button
                type="button"
                onClick={async () => {
                  await onDisable();
                  close();
                }}
                disabled={user.status === "DISABLED" || disable.isPending}
                className="block w-full px-3 py-2 text-left text-sm text-(--color-error) hover:bg-(--color-surface-alt) disabled:opacity-50 cursor-pointer disabled:pointer-events-none"
              >
                Disable
              </button>
            </>
          )}
        </RowActionMenu>
      </td>
    </tr>
  );
}

export const AdminUserRow = memo(AdminUserRowComponent);