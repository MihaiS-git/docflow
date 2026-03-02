"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { useAuth } from "@/lib/auth/useAuth";
import { fetchActiveTenants } from "@/lib/admin/adminTenants";
import type { AdminTenant, Page } from "@/types/admin/Tenant";

type TenantRole = "MEMBER" | "EXECUTOR" | "REVIEWER" | "MANAGER";

export default function InvitesPage() {
  const { status, identity } = useAuth();

  const isAdmin = status === "AUTH" && identity?.roles.includes("ADMIN");

  const [tenants, setTenants] = useState<AdminTenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = useState("");

  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [department, setDepartment] = useState("");
  const [tenantRole, setTenantRole] = useState<TenantRole>("MEMBER");

  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin) return;
    loadTenants();
  }, [isAdmin]);

  async function loadTenants() {
    try {
      const page: Page<AdminTenant> = await fetchActiveTenants();

      setTenants(page.content);

      if (page.content.length > 0) {
        setSelectedTenantId(page.content[0].id);
      }
    } catch (err) {
      console.error(err);
      setError("Failed to load tenants.");
    }
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!selectedTenantId) {
      setError("Tenant required.");
      return;
    }

    setLoading(true);
    setError(null);
    setSuccess(null);

    try {
      await apiFetch<void>(`/api/tenants/${selectedTenantId}/invites`, {
        method: "POST",
        body: JSON.stringify({
          email,
          firstName,
          lastName,
          jobTitle: jobTitle || null,
          department: department || null,
          tenantRole,
        }),
      });

      setSuccess("Invite sent successfully.");

      // reset form
      setEmail("");
      setFirstName("");
      setLastName("");
      setJobTitle("");
      setDepartment("");
      setTenantRole("MEMBER");
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

  if (!isAdmin) {
    return <div className="p-8">Access denied</div>;
  }

  return (
    <div className="p-8 max-w-lg space-y-6">
      <h1 className="text-xl font-semibold">Create Tenant Invite</h1>

      <form onSubmit={onSubmit} className="space-y-3">
        <select
          required
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
          required
          type="email"
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

        <select
          value={tenantRole}
          onChange={(e) => setTenantRole(e.target.value as TenantRole)}
          className="bg-white text-black p-2 w-full"
        >
          <option value="MEMBER">Member</option>
          <option value="EXECUTOR">Executor</option>
          <option value="REVIEWER">Reviewer</option>
          <option value="MANAGER">Manager</option>
        </select>

        <button
          type="submit"
          disabled={loading}
          className="bg-blue-600 text-white px-4 py-2 rounded"
        >
          {loading ? "Sending…" : "Send Invite"}
        </button>
      </form>

      {success && <p className="text-green-600">{success}</p>}

      {error && <p className="text-red-600">{error}</p>}
    </div>
  );
}
