"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { apiFetch } from "@/lib/apiFetch";

export default function InvitePage() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [error, setError] = useState<string | undefined>(undefined);

  const tokenMissing = !token;

  useEffect(() => {
    if (tokenMissing) {
      return;
    }

    let cancelled = false;

    async function validateInvite() {
      try {
        await apiFetch<void>(
          `/api/invites/validate?token=${encodeURIComponent(token!)}`,
          {
            method: "POST",
            csrfMode: "anonymous",
          },
        );
        console.log("Called POST /api/invites/validate");

        if (cancelled) return;

        // Full redirect required for OAuth2
        window.location.href = `${process.env.NEXT_PUBLIC_API_BASE_URL}/oauth2/authorization/keycloak`;
      } catch {
        if (cancelled) return;
        setError("Invite link invalid or expired.");
      }
    }

    validateInvite();

    return () => {
      cancelled = true;
    };
  }, [token, tokenMissing]);

  const effectiveError = tokenMissing
    ? "Invite link invalid or expired."
    : error;

  return (
    <main className="min-h-screen flex items-center justify-center px-4">
      <div className="w-full max-w-md rounded border p-6 text-center">
        {!effectiveError ? (
          <>
            <h1 className="text-lg font-semibold mb-2">
              Validating invitation…
            </h1>
            <p className="text-sm text-gray-600">
              You will be redirected shortly.
            </p>
          </>
        ) : (
          <>
            <h1 className="text-lg font-semibold mb-2 text-red-600">
              Invitation error
            </h1>
            <p className="text-sm">{effectiveError}</p>
          </>
        )}
      </div>
    </main>
  );
}
