"use client";

import { useMemo, useRef, useState } from "react";
import {
  activateUser,
  lockUser,
  disableUser,
  assignRole,
  revokeRole,
} from "@/lib/admin/adminUsers";
import { AdminUser } from "@/types/admin/AdminUser";
import { ForbiddenError } from "@/lib/apiErrors";
import { toast } from "sonner";
import { X } from "lucide-react";

import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import { useDismissibleLayer } from "@/hooks/useDismissibleLayer";

type Props = {
  user: AdminUser;
  allRoles: string[];
  onUserUpdated: (user: AdminUser) => void;
};

export function AdminUserRow({ user, allRoles, onUserUpdated }: Props) {
  const [selectedRole, setSelectedRole] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuStyle, setMenuStyle] = useState<React.CSSProperties>({});

  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const menuRef = useRef<HTMLDivElement | null>(null);

  const userRoles = useMemo(() => user.roles ?? [], [user.roles]);

  const availableRoles = useMemo(
    () => allRoles.filter((r) => !userRoles.includes(r)),
    [allRoles, userRoles],
  );

  const onGrant = async () => {
    if (!selectedRole) return;

    await assignRole(user.id, selectedRole);

    onUserUpdated({
      ...user,
      roles: [...(user.roles ?? []), selectedRole],
    });

    setSelectedRole("");
  };

  const onActivate = async () => {
    await activateUser(user.id);
    onUserUpdated({ ...user, status: "ACTIVE" });
    setMenuOpen(false);
  };

  const onLock = async () => {
    try {
      await lockUser(user.id);
      onUserUpdated({ ...user, status: "LOCKED" });
      setMenuOpen(false);
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
  };

  const onDisable = async () => {
    try {
      await disableUser(user.id);
      onUserUpdated({ ...user, status: "DISABLED" });
      setMenuOpen(false);
    } catch (err) {
      if (
        err instanceof ForbiddenError &&
        err.errorCode === "SELF_ACTION_FORBIDDEN"
      ) {
        toast.error("You cannot disable your own account");
        return;
      }
    }
  };

  const toggleMenu = () => {
    const rect = buttonRef.current?.getBoundingClientRect();
    if (!rect) return;

    const menuHeight = 150;
    const spaceBelow = window.innerHeight - rect.bottom;
    const openUp = spaceBelow < menuHeight;

    const top = openUp ? rect.top - menuHeight - 4 : rect.bottom + 4;
    const left = rect.right - 160;

    setMenuStyle({
      position: "fixed",
      top,
      left,
      width: 160,
      zIndex: 1000,
    });

    setMenuOpen((v) => !v);
  };

  useDismissibleLayer({
    open: menuOpen,
    onClose: () => setMenuOpen(false),
    triggerRef: buttonRef,
    contentRef: menuRef,
  });

  return (
    <tr className="border-b border-(--color-table-border) hover:bg-(--color-table-row-hover) align-top">
      <td className="px-4 py-3 text-sm text-(--color-text-primary)">
        {user.email}
      </td>

      <td className="px-4 py-3 text-sm text-(--color-text-primary)">
        <span className="inline-flex items-center rounded-md bg-(--color-surface-alt) px-2 py-0.5 text-xs font-medium">
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
                className="inline-flex items-center gap-1 rounded-md bg-(--color-surface-alt) h-5 px-2 text-xs leading-none"
              >
                {role}

                <button
                  type="button"
                  onClick={async () => {
                    await revokeRole(user.id, role);

                    onUserUpdated({
                      ...user,
                      roles: userRoles.filter((r) => r !== role),
                    });
                  }}
                  className="ml-1 flex items-center justify-center text-(--color-error) hover:opacity-80"
                  aria-label={`Revoke ${role}`}
                >
                  <X size={12} strokeWidth={2.5} />
                </button>
              </span>
            ))
          )}
        </div>

        <div className="flex items-center gap-2 mt-2">
          <Select
            value={selectedRole}
            onChange={(e) => setSelectedRole(e.target.value)}
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

      <td className="px-4 py-3">
        <Button
          ref={buttonRef}
          size="icon"
          variant="ghost"
          aria-label="User actions"
          onClick={toggleMenu}
        >
          ⋯
        </Button>

        {menuOpen && (
          <div
            ref={menuRef}
            style={menuStyle}
            className="rounded-md border border-(--color-border) bg-(--color-surface) shadow-md"
          >
            <button
              type="button"
              onClick={onActivate}
              disabled={user.status === "ACTIVE"}
              className="block w-full text-left px-3 py-2 text-sm hover:bg-(--color-surface-alt) disabled:opacity-50"
            >
              Activate
            </button>

            <button
              type="button"
              onClick={onLock}
              disabled={user.status === "LOCKED"}
              className="block w-full text-left px-3 py-2 text-sm hover:bg-(--color-surface-alt) disabled:opacity-50"
            >
              Lock
            </button>

            <button
              type="button"
              onClick={onDisable}
              disabled={user.status === "DISABLED"}
              className="block w-full text-left px-3 py-2 text-sm text-(--color-error) hover:bg-(--color-surface-alt) disabled:opacity-50"
            >
              Disable
            </button>
          </div>
        )}
      </td>
    </tr>
  );
}
