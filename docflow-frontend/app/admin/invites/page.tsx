"use client";

import { useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

export default function AdminInvitesPage() {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    setLoading(true);
    setSuccess(null);
    setError(null);

    try {
      await apiFetch<void>(
        `/api/admin/invites?email=${encodeURIComponent(email)}`,
        {
          method: "POST",
        },
      );

      setSuccess("Invite sent successfully.");
      setEmail("");
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
    <div style={{ maxWidth: 420, padding: 24 }}>
      <h1>Send Admin Invite</h1>

      <form onSubmit={onSubmit}>
        <div>
          <label htmlFor="email">Email</label>
        </div>

        <div>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={loading}
            className="bg-white text-black"
          />
        </div>

        <div style={{ marginTop: 12 }}>
          <button type="submit" disabled={loading || !email}>
            {loading ? "Sending..." : "Send Invite"}
          </button>
        </div>
      </form>

      {success && (
        <p style={{ marginTop: 12, color: "green" }}>{success}</p>
      )}

      {error && (
        <p style={{ marginTop: 12, color: "red" }}>{error}</p>
      )}
    </div>
  );
}
