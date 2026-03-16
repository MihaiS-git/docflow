"use client";

import { useEffect, useMemo } from "react";
import { usePathname, useRouter } from "next/navigation";

import { getRequiredRole } from "@/lib/auth/routePermissions";
import { useAuthSelector } from "@/hooks/useAuthSelector";

export default function RoutePermissionGuard({
  children,
}: {
  children: React.ReactNode;
}) {
  const status = useAuthSelector((s) => s.status);
  const identity = useAuthSelector((s) => s.identity);

  const pathname = usePathname();
  const router = useRouter();

  const requiredRole = useMemo(() => getRequiredRole(pathname), [pathname]);

  const hasRole = !requiredRole || identity?.roles?.includes(requiredRole);

  useEffect(() => {
    if (status !== "LOADING" && requiredRole && !hasRole) {
      router.replace(`/access-denied?role=${requiredRole}`);
    }
  }, [status, requiredRole, hasRole, router]);

  if (status === "LOADING") {
    return (
      <div className="min-h-screen flex items-center justify-center">
        Loading…
      </div>
    );
  }

  if (requiredRole && !hasRole) {
    return null;
  }

  return <>{children}</>;
}