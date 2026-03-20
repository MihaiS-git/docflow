"use client";

import { memo, useMemo, useState } from "react";
import { X } from "lucide-react";
import { toast } from "sonner";

import {
  activateUser,
  assignRole,
  disableUser,
  lockUser,
  revokeRole,
} from "@/lib/admin/adminUsers";
import { ForbiddenError } from "@/lib/apiErrors";

import type { AdminUser } from "@/types/admin/AdminUser";

import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import RowActionMenu from "@/components/ui/RowActionMenu";

type Props = {
  user: AdminUser;
  allRoles: string[];
  onUserUpdated: (user: AdminUser) => void;
};

function AdminUserRowComponent({ user, allRoles, onUserUpdated }: Props) {
  const [selectedRole, setSelectedRole] = useState("");
  const [revokingRole, setRevokingRole] = useState<string | null>(null);

  const userRoles = useMemo(() => user.roles ?? [], [user.roles]);

  const availableRoles = useMemo(
    () => allRoles.filter((role) => !userRoles.includes(role)),
    [allRoles, userRoles],
  );

  async function onGrant() {
    if (!selectedRole) return;

    await assignRole(user.id, selectedRole);

    onUserUpdated({
      ...user,
      roles: [...userRoles, selectedRole],
    });

    setSelectedRole("");
  }

  async function onActivate() {
    await activateUser(user.id);
    onUserUpdated({ ...user, status: "ACTIVE" });
  }

  async function onLock() {
    try {
      await lockUser(user.id);
      onUserUpdated({ ...user, status: "LOCKED" });
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
    try {
      await disableUser(user.id);
      onUserUpdated({ ...user, status: "DISABLED" });
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
                  disabled={revokingRole === role}
                  onClick={async () => {
                    if (revokingRole === role) return;

                    setRevokingRole(role);

                    const updatedRoles = userRoles.filter((r) => r !== role);
                    onUserUpdated({ ...user, roles: updatedRoles });

                    try {
                      await revokeRole(user.id, role);
                    } catch {
                      onUserUpdated({ ...user, roles: userRoles });
                    } finally {
                      setRevokingRole(null);
                    }
                  }}
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
            disabled={availableRoles.length === 0}
          >
            <option value="">Add role…</option>

            {availableRoles.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </Select>

          <Button size="sm" disabled={!selectedRole} onClick={onGrant}>
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
                disabled={user.status === "ACTIVE"}
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
                disabled={user.status === "LOCKED"}
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
                disabled={user.status === "DISABLED"}
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