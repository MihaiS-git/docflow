"use client";

import { useAuth } from "@/lib/auth/useAuth";
import RoutePermissionGuard from "@/components/auth/RoutePermissionGuard";

export default function ProtectedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { status, isAuthenticated } = useAuth();

  if (status === "LOADING") {
    return (
      <div className="min-h-screen flex items-center justify-center">
        Loading…
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center space-y-2">
          <h1 className="text-xl font-semibold">Login required</h1>
          <p className="text-sm opacity-70">Please sign in to continue.</p>
        </div>
      </div>
    );
  }

  return <RoutePermissionGuard>{children}</RoutePermissionGuard>;
}