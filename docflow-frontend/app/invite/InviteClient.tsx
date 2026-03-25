"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { apiFetch } from "@/lib/apiFetch";

type ActivationState = "activating" | "ready" | "error";

export default function InviteClient() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const tokenMissing = !token;

  const [activationState, setActivationState] =
    useState<ActivationState>("activating");
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (tokenMissing) {
      return;
    }

    let cancelled = false;

    async function activateInvite() {
      try {
        if (!token) {
          throw new Error("Missing invite token");
        }

        await apiFetch<void>(
          `/api/invites/activate?token=${encodeURIComponent(token)}`,
          { method: "POST" },
        );

        if (cancelled) return;
        setActivationState("ready");
      } catch (err) {
        if (cancelled) return;

        setError(
          err instanceof Error ? err.message : "Invite activation failed.",
        );
        setActivationState("error");
      }
    }

    void activateInvite();

    return () => {
      cancelled = true;
    };
  }, [token, tokenMissing]);

  function handleContinueToLogin() {
    window.location.href = `${process.env.NEXT_PUBLIC_API_BASE_URL}/oauth2/authorization/keycloak`;
  }

  const effectiveState: ActivationState = tokenMissing
    ? "error"
    : activationState;

  const effectiveError = tokenMissing
    ? "Invite link invalid or expired."
    : (error ?? "Invite activation failed.");

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-md rounded border p-6 text-center">
        {effectiveState === "activating" && (
          <>
            <h1 className="mb-2 text-lg font-semibold">
              Activating invitation…
            </h1>
            <p className="text-sm text-gray-600">
              Please wait while we prepare your access.
            </p>
          </>
        )}

        {effectiveState === "ready" && (
          <>
            <h1 className="mb-2 text-lg font-semibold">Invitation activated</h1>
            <p className="mb-4 text-sm text-gray-600">
              If this is your first access, check your email and complete the
              password setup message that was sent to you. After that, continue
              to sign in.
            </p>

            <button
              type="button"
              onClick={handleContinueToLogin}
              className="rounded border px-4 py-2 text-sm font-medium"
            >
              Continue to sign in
            </button>
          </>
        )}

        {effectiveState === "error" && (
          <>
            <h1 className="mb-2 text-lg font-semibold text-red-600">
              Invitation error
            </h1>
            <p className="text-sm">{effectiveError}</p>
          </>
        )}
      </div>
    </main>
  );
}
