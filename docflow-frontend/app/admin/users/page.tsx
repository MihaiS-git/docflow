"use client";

import { AdminUserRow } from "@/app/admin/AdminUserRow";
import { useEffect, useState } from "react";
import { AdminUser } from "@/types/admin/AdminUser";
import { fetchAdminUsers } from "@/lib/admin/adminUsers";
import { RequireAdmin } from "@/lib/auth/RequireAdmin";
import { fetchAdminRoles } from "@/lib/admin/adminRoles";

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [roles, setRoles] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([fetchAdminUsers(), fetchAdminRoles()])
      .then(([users, roles]) => {
        setUsers(users);
        setRoles(roles);
      })
      .finally(() => setLoading(false));
  }, []);

  const updateUser = (updated: AdminUser) => {
    setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
  };

  if (loading) return <div>Loading…</div>;

  return (
    <RequireAdmin>
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
    </RequireAdmin>
  );
}
