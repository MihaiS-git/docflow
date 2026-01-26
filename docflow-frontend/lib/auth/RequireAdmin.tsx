"use client";

import { useAuth } from "./useAuth";

export function RequireAdmin({ children }: { children: React.ReactNode }) {
  const { identity } = useAuth();

  if (!identity) return null;

  if (!identity.roles.includes("ADMIN")) {
    return (
      <div className="text-red-600">
        Access denied (ADMIN only)
      </div>
    );
  }

  return <>{children}</>;
}
