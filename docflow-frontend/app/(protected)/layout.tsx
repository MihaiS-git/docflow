"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { useAuth } from "@/lib/auth/useAuth";
import RoutePermissionGuard from "@/components/auth/RoutePermissionGuard";

export default function ProtectedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { status, isAuthenticated } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status !== "LOADING" && !isAuthenticated) {
      router.replace("/");
    }
  }, [status, isAuthenticated, router]);

  if (status === "LOADING") {
    return (
      <div className="min-h-screen flex items-center justify-center">
        Loading…
      </div>
    );
  }

  if (!isAuthenticated) {
    return null;
  }

  return <RoutePermissionGuard>{children}</RoutePermissionGuard>;
}