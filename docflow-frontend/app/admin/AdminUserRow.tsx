"use client";

import { useMemo, useState } from "react";
import {
  activateUser,
  lockUser,
  disableUser,
  assignRole,
  revokeRole,
} from "@/lib/admin/adminUsers";
import { AdminUser } from "@/types/admin/AdminUser";

type Props = {
  user: AdminUser;
  allRoles: string[];
};

export function AdminUserRow({ user, allRoles }: Props) {
  const [selectedRole, setSelectedRole] = useState("");

  const reload = () => window.location.reload();

  // Runtime-safe: backend may not provide roles yet
  const userRoles = useMemo(() => user.roles ?? [], [user.roles]);

  const availableRoles = useMemo(
    () => allRoles.filter((r) => !userRoles.includes(r)),
    [allRoles, userRoles],
  );

  const onGrant = async () => {
    if (!selectedRole) return;
    await assignRole(user.id, selectedRole);
    reload();
  };

  return (
    <tr className="border-b align-top">
      <td>{user.email}</td>
      <td>{user.status}</td>

      <td>
        <div className="flex flex-wrap gap-2">
          {userRoles.length === 0 ? (
            <span className="text-sm opacity-70">No roles loaded</span>
          ) : (
            userRoles.map((role) => (
              <span
                key={role}
                className="flex items-center gap-1 bg-gray-200 px-2 py-1 rounded"
              >
                {role}
                <button
                  onClick={() => revokeRole(user.id, role).then(reload)}
                  className="text-red-600 font-bold"
                  title={`Revoke ${role}`}
                >
                  ×
                </button>
              </span>
            ))
          )}
        </div>

        <div className="flex gap-2 mt-2">
          <select
            value={selectedRole}
            onChange={(e) => setSelectedRole(e.target.value)}
            className="border px-2 py-1"
          >
            <option value="">Add role…</option>
            {availableRoles.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </select>

          <button
            disabled={!selectedRole}
            onClick={onGrant}
            className="px-2 py-1 bg-blue-600 text-white disabled:opacity-40"
          >
            Add
          </button>
        </div>
      </td>

      <td className="flex gap-2">
        <button
          onClick={() => activateUser(user.id).then(reload)}
          disabled={user.status === "ACTIVE"}
          className="px-2 py-1 bg-green-600 text-white disabled:opacity-40"
        >
          Activate
        </button>

        <button
          onClick={() => lockUser(user.id).then(reload)}
          disabled={user.status === "LOCKED"}
          className="px-2 py-1 bg-yellow-500 text-white disabled:opacity-40"
        >
          Lock
        </button>

        <button
          onClick={() => disableUser(user.id).then(reload)}
          disabled={user.status === "DISABLED"}
          className="px-2 py-1 bg-red-600 text-white disabled:opacity-40"
        >
          Disable
        </button>
      </td>
    </tr>
  );
}
