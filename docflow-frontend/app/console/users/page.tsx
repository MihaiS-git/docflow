"use client";

import { AdminUserRow } from "@/app/console/AdminUserRow";
import { useEffect, useState } from "react";
import { AdminUser } from "@/types/admin/AdminUser";
import { fetchAdminUsers } from "@/lib/admin/adminUsers";
import { fetchAdminRoles } from "@/lib/admin/adminRoles";
/* import { useAuth } from "@/lib/auth/useAuth"; */

export default function AdminUsersPage() {
  /* const { status, identity } = useAuth(); */

/*   const authLoading = status === "LOADING";
  const isAdmin = identity?.roles.includes("ADMIN"); */

  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    /* if (status !== "AUTH" || !isAdmin) return; */

    Promise.all([fetchAdminUsers(), fetchAdminRoles()])
      .then(([users, roles]) => {
        setUsers(users);
        setRoles(roles);
      })
      .finally(() => setLoading(false));
  }, [/* status, isAdmin */]);

  /* if (authLoading) return <div>Loading auth...</div>;

  if (status !== "AUTH") return <div>Please login.</div>;

  if (!isAdmin)
    return <div className="text-red-600">Access denied (ADMIN only)</div>; */

  if (loading) return <div>Loading admin data…</div>;

  const updateUser = (updated: AdminUser) => {
    setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
  };

  return (
    <>
      <h1 className="text-xl font-bold mb-4">Admin - Users</h1>

      <table className="w-full border">
        <thead>
          <tr>
            <th>Email</th>
            <th>Status</th>
            <th>Roles</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <AdminUserRow
              key={u.id}
              user={u}
              allRoles={roles}
              onUserUpdated={updateUser}
            />
          ))}
        </tbody>
      </table>
    </>
  );
}
