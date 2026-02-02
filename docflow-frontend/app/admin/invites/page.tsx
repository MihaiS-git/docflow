"use client";

import { useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

export default function AdminInvitesPage() {
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [department, setDepartment] = useState("");

  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
  );
}
