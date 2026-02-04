"use client";

import { apiFetch } from "@/lib/apiFetch";
import { useAuth } from "@/lib/auth/useAuth";
import { useState } from "react";

export default function BootstrapActivatePage() {
  const { refresh, logout } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activate = async () => {
    setLoading(true);
    setError(null);
    try {
      await apiFetch<void>("/api/bootstrap/activate", {
        method: "POST",
      });
      await refresh(); // re-fetch identity + local user
    } catch {
      setError("Activation failed. Please contact support.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-black text-white">
      <div className="max-w-md w-full space-y-6 text-center">
        <h1 className="text-2xl font-bold">Initial setup</h1>

        <p className="opacity-80">
          This application has not been initialized yet.
          Activate the administrator account to continue.
        </p>

        {error && <p className="text-red-400">{error}</p>}

        <button
          onClick={activate}
          disabled={loading}
          className="w-full px-4 py-2 bg-green-600 hover:bg-green-700 rounded disabled:opacity-50"
        >
          {loading ? "Activating…" : "Activate administrator"}
        </button>

        <button
          onClick={logout}
          className="w-full px-4 py-2 border border-zinc-600 rounded opacity-70 hover:opacity-100"
        >
          Logout
        </button>
      </div>
    </div>
  );
}
