"use client";

import { useAuth } from "@/lib/auth/useAuth";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { getRequiredRole } from "@/lib/auth/routePermissions";

export default function RoutePermissionGuard({
  children,
}: {
  children: React.ReactNode;
}) {
  const { status, identity } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const requiredRole = getRequiredRole(pathname);

  const hasRole =
    !requiredRole || identity?.roles?.includes(requiredRole);

  useEffect(() => {
    if (status !== "LOADING" && !hasRole && requiredRole) {
      router.replace(`/access-denied?role=${requiredRole}`);
    }
  }, [status, hasRole, requiredRole, router]);

  if (status === "LOADING") {
    return (
      <div className="min-h-screen flex items-center justify-center">
        Loading…
      </div>
    );
  }

  if (!hasRole && requiredRole) return null;

  return <>{children}</>;
}