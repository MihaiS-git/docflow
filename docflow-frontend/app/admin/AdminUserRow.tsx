"use client";

import { lockUser, disableUser, activateUser } from "@/lib/adminUsers";
import { AdminUser } from "@/types/admin/AdminUser";

type Props = {
  user: AdminUser;
};

export function AdminUserRow({ user }: Props) {
  const onActivate = async () => {
    await activateUser(user.id);
    window.location.reload();
    alert("User activated");
  };

  const onLock = async () => {
    await lockUser(user.id);
    window.location.reload();
    alert("User locked");
  };

  const onDisable = async () => {
    await disableUser(user.id);
    window.location.reload();
    alert("User disabled");
  };

  return (
    <tr className="border-b">
      <td>{user.email}</td>
      <td>{user.status}</td>
      <td className="flex gap-2">
        <button
          onClick={onActivate}
          disabled={user.status === "ACTIVE"}
          className="px-2 py-1 bg-green-500 text-white disabled:opacity-40"
        >
          Activate
        </button>
        <button
          onClick={onLock}
          disabled={user.status === "LOCKED"}
          className="px-2 py-1 bg-yellow-500 text-white disabled:opacity-40"
        >
          Lock
        </button>
        <button
          onClick={onDisable}
          disabled={user.status === "DISABLED"}
          className="px-2 py-1 bg-red-600 text-white disabled:opacity-40"
        >
          Disable
        </button>
      </td>
    </tr>
  );
}
