"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { AdminInvite } from "@/types/admin/Invite";
import {
  cleanupInvites,
  fetchAdminInvites,
  revokeInvite,
} from "@/lib/admin/adminInvites";
import { getEffectiveInviteStatus } from "@/lib/admin/inviteStatus";
import { useAuth } from "@/lib/auth/useAuth";

export default function AdminInvitesPage() {
  const { status, identity } = useAuth();

  const isAdmin =
    status === "AUTH" && identity?.roles.includes("ADMIN");

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
    loadInvites();
  }, [isAdmin]);

  if (status === "LOADING") {
    return <div>Loading auth…</div>;
  }

  if (status !== "AUTH") {
    return <div>Please login.</div>;
  }

  if (!isAdmin) {
    return (
      <div className="text-red-600">
        Access denied (ADMIN only)
      </div>
    );
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

    setLoading(true);
    setSuccess(null);
    setError(null);

    try {
      await apiFetch<void>("/api/admin/invites", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
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

  return (
    <>
      <div style={{ maxWidth: 420, padding: 24 }}>
        <h1>Send Admin Invite</h1>

        <form onSubmit={onSubmit}>
          <div>
            <label>Email</label>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
              className="bg-white text-black"
            />
          </div>

          <div>
            <label>First name</label>
            <input
              required
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              disabled={loading}
              className="bg-white text-black"
            />
          </div>

          <div>
            <label>Last name</label>
            <input
              required
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              disabled={loading}
              className="bg-white text-black"
            />
          </div>

          <div>
            <label>Job title</label>
            <input
              value={jobTitle}
              onChange={(e) => setJobTitle(e.target.value)}
              disabled={loading}
              className="bg-white text-black"
            />
          </div>

          <div>
            <label>Department</label>
            <input
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              disabled={loading}
              className="bg-white text-black"
            />
          </div>

          <div style={{ marginTop: 12 }}>
            <button
              type="submit"
              disabled={loading || !email || !firstName || !lastName}
            >
              {loading ? "Sending..." : "Send Invite"}
            </button>
          </div>
        </form>

        {success && <p style={{ marginTop: 12, color: "green" }}>{success}</p>}
        {error && <p style={{ marginTop: 12, color: "red" }}>{error}</p>}
      </div>

      <div style={{ maxWidth: 420, padding: 24 }}>
        <h2 className="mt-10 mb-4 text-lg font-semibold">Invites</h2>

        <button onClick={onCleanup} className="mb-4">
          Cleanup expired invites & orphaned users
        </button>

        <table className="w-full border">
          <thead>
            <tr>
              <th>Email</th>
              <th>Status</th>
              <th>Age</th>
              <th>Expires</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {invites.length === 0 && !loadingInvites ? (
              <tr>
                <td colSpan={5} className="text-center opacity-60">
                  No invites
                </td>
              </tr>
            ) : (
              <>
                {invites.map((invite) => {
                  const status = getEffectiveInviteStatus(invite);

                  return (
                    <tr key={invite.id}>
                      <td>{invite.email}</td>
                      <td>{status}</td>
                      <td>{Math.floor(invite.ageSeconds / 86400)} days</td>
                      <td>
                        {invite.expiresAt
                          ? new Date(invite.expiresAt).toLocaleDateString()
                          : "—"}
                      </td>
                      <td>
                        {status === "PENDING" && (
                          <button onClick={() => onRevoke(invite.id)}>
                            Revoke
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </>
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
