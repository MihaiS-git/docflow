"use client";

import { AdminUserRow } from "@/app/admin/AdminUserRow";
import { useEffect, useState } from "react";
import { AdminUser } from "@/types/admin/AdminUser";
import { fetchAdminUsers } from "@/lib/adminUsers";
import { RequireAdmin } from "@/lib/auth/RequireAdmin";

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchAdminUsers()
      .then(setUsers)
      .finally(() => setLoading(false));
  }, []);
  return (
    <RequireAdmin>
      <h1 className="text-xl font-bold mb-4">Admin – Users</h1>

      {loading ? (
        <div>Loading…</div>
      ) : (
        <table className="w-full border">
          <thead>
            <tr>
              <th>Email</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <AdminUserRow key={u.id} user={u} />
            ))}
          </tbody>
        </table>
      )}
    </RequireAdmin>
  );
}
