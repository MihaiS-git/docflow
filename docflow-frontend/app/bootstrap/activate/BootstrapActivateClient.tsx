"use client";

import { activateBootstrap } from "@/lib/auth/auth";
import { ApiError, ForbiddenError } from "@/lib/apiErrors";
import { useState } from "react";

function mapBootstrapError(err: unknown): string {
  if (err instanceof ForbiddenError) {
    switch (err.errorCode) {
      case "BOOTSTRAP_ACTIVATION_DENIED":
        return "You are not allowed to activate this system.";
      case "BOOTSTRAP_ACTIVATION_NOT_ALLOWED":
        return "This system has already been initialized.";
      default:
        return err.message;
    }
  }

  if (err instanceof ApiError) {
    return err.message;
  }

  return "Bootstrap activation failed. Please contact support.";
}

function postLogout() {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = `${process.env.NEXT_PUBLIC_API_BASE_URL}/api/auth/logout`;
  document.body.appendChild(form);
  form.submit();
}

export default function BootstrapActivateClient() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activate = async () => {
    setLoading(true);
    setError(null);
    try {
      await activateBootstrap();
      // After activation, go to protected home; AuthProvider there will refresh.
      window.location.href = "/";
    } catch (err: unknown) {
      setError(mapBootstrapError(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {error && <p className="text-red-500">{error}</p>}

      <button
        onClick={activate}
        disabled={loading}
        className="w-full px-4 py-2 bg-green-600 hover:bg-green-700 rounded disabled:opacity-50"
      >
        {loading ? "Activating…" : "Activate system"}
      </button>

      <button
        onClick={postLogout}
        className="w-full px-4 py-2 bg-zinc-700 hover:bg-zinc-800 rounded"
      >
        Cancel
      </button>
    </>
  );
}
