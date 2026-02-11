"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/lib/apiErrors";
import { AdminInvite } from "@/types/admin/Invite";
import { AdminTenant } from "@/types/admin/Tenant";
import {
  cleanupInvites,
  fetchAdminInvites,
  revokeInvite,
} from "@/lib/admin/adminInvites";
import { fetchActiveTenants } from "@/lib/admin/adminTenants";
import { useAuth } from "@/lib/auth/useAuth";
import { apiFetch } from "@/lib/apiFetch";

export default function AdminInvitesPage() {
  const { status, identity } = useAuth();

  const isAdmin = status === "AUTH" && identity?.roles.includes("ADMIN");

  const [tenants, setTenants] = useState<AdminTenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState<string>("");

  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [department, setDepartment] = useState("");

  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [invites, setInvites] = useState<AdminInvite[]>([]);
  const [loadingInvites, setLoadingInvites] = useState(false);

  useEffect(() => {
    if (!isAdmin) return;

    loadTenants();
    loadInvites();
  }, [isAdmin]);

  async function loadTenants() {
    const page = await fetchActiveTenants();
    setTenants(page.content);
    if (page.content.length > 0) {
      setSelectedTenantId(page.content[0].id);
    }
  }

  async function loadInvites() {
    setLoadingInvites(true);
    try {
      const page = await fetchAdminInvites();
      setInvites(page.content);
    } finally {
      setLoadingInvites(false);
    }
  }

  async function onRevoke(id: string) {
    if (!confirm("Revoke this invite?")) return;
    await revokeInvite(id);
    await loadInvites();
  }

  async function onCleanup() {
    if (!confirm("Cleanup expired invites and orphaned users?")) return;
    const res = await cleanupInvites();
    alert(
      `Deleted ${res.deletedInvites} invites and ${res.deletedUsers} users`,
    );
    await loadInvites();
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!selectedTenantId) {
      setError("Tenant is required.");
      return;
    }

    setLoading(true);
    setSuccess(null);
    setError(null);

    try {
      await apiFetch<void>("/api/admin/invites", {
        method: "POST",
        body: JSON.stringify({
          targetTenantId: selectedTenantId,
          email,
          firstName,
          lastName,
          jobTitle: jobTitle || null,
          department: department || null,
        }),
      });

      setSuccess("Invite sent successfully.");
      setEmail("");
      setFirstName("");
      setLastName("");
      setJobTitle("");
      setDepartment("");

      await loadInvites();
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
    return <div>Access denied</div>;
  }

  return (
    <div className="p-8 space-y-10">
      <div className="max-w-lg space-y-4">
        <h1 className="text-xl font-semibold">Create Invite</h1>

        <form onSubmit={onSubmit} className="space-y-3">
          <select
            value={selectedTenantId}
            onChange={(e) => setSelectedTenantId(e.target.value)}
            className="bg-white text-black p-2 w-full"
          >
            {tenants.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>

          <input
            type="email"
            required
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="bg-white text-black p-2 w-full"
          />

          <input
            required
            placeholder="First name"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            className="bg-white text-black p-2 w-full"
          />

          <input
            required
            placeholder="Last name"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            className="bg-white text-black p-2 w-full"
          />

          <input
            placeholder="Job title"
            value={jobTitle}
            onChange={(e) => setJobTitle(e.target.value)}
            className="bg-white text-black p-2 w-full"
          />

          <input
            placeholder="Department"
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
            className="bg-white text-black p-2 w-full"
          />

          <button
            type="submit"
            disabled={loading}
            className="bg-blue-600 text-white px-4 py-2 rounded"
          >
            {loading ? "Sending..." : "Send Invite"}
          </button>
        </form>

        {success && <p className="text-green-600">{success}</p>}
        {error && <p className="text-red-600">{error}</p>}
      </div>

      <div>
        <h2 className="text-lg font-semibold mb-4">Invites</h2>

        <button onClick={onCleanup} className="mb-4 underline">
          Cleanup expired invites
        </button>

        <table className="w-full border">
          <thead>
            <tr>
              <th>Email</th>
              <th>Tenant</th>
              <th>Status</th>
              <th>Age</th>
              <th>Expires</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {loadingInvites ? (
              <tr>
                <td colSpan={6} className="text-center py-4">
                  Loading invites…
                </td>
              </tr>
            ) : invites.length === 0 ? (
              <tr>
                <td colSpan={6} className="text-center py-4">
                  No invites found.
                </td>
              </tr>
            ) : (
              invites.map((invite) => (
                <tr key={invite.id}>
                  <td>{invite.email}</td>
                  <td>{invite.tenantName}</td>
                  <td>{invite.status}</td>
                  <td>{Math.floor(invite.ageSeconds / 86400)} days</td>
                  <td>{new Date(invite.expiresAt).toLocaleDateString()}</td>
                  <td>
                    {invite.status === "PENDING" && (
                      <button onClick={() => onRevoke(invite.id)}>
                        Revoke
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
