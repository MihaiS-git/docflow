"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/useAuth";
import { ApiError } from "@/lib/apiErrors";
import {
  fetchAllTenants,
  createTenant,
} from "@/lib/admin/adminTenants";
import type { AdminTenant } from "@/types/admin/Tenant";

export default function TenantClient() {
  const { status, identity } = useAuth();

  const isAdmin =
    status === "AUTH" && identity?.roles.includes("ADMIN");

  const [tenants, setTenants] = useState<AdminTenant[]>([]);
  const [name, setName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin) return;
    loadTenants();
  }, [isAdmin]);

  async function loadTenants() {
    const page = await fetchAllTenants();
    setTenants(page.content);
  }

  async function handleCreate(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!name.trim()) {
      setError("Tenant name is required.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await createTenant(name.trim());
      setName("");
      await loadTenants();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError("Unexpected error occurred.");
      }
    } finally {
      setLoading(false);
    }
  }

  if (status !== "AUTH" || !isAdmin) {
    return <div className="p-8">Access denied</div>;
  }

  return (
    <div className="p-8 space-y-10">

      <div className="max-w-md space-y-4">
        <h1 className="text-xl font-semibold">Create Tenant</h1>

        <form onSubmit={handleCreate} className="space-y-3">
          <input
            placeholder="Tenant name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="bg-white text-black p-2 w-full"
            disabled={loading}
          />

          <button
            type="submit"
            disabled={loading}
            className="bg-blue-600 text-white px-4 py-2 rounded"
          >
            {loading ? "Creating..." : "Create Tenant"}
          </button>
        </form>

        {error && <p className="text-red-600">{error}</p>}
      </div>

      <div>
        <h2 className="text-lg font-semibold mb-4">Existing Tenants</h2>

        <table className="w-full border">
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Bootstrap</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {tenants.map((t) => (
              <tr key={t.id}>
                <td>{t.name}</td>
                <td>{t.status}</td>
                <td>{t.bootstrapEnabled ? "Yes" : "No"}</td>
                <td>
                  {new Date(t.createdAt).toLocaleDateString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

    </div>
  );
}
