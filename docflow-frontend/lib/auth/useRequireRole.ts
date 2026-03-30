"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/useAuth";
import { useHasRole } from "@/lib/auth/useHasRole";
import { RealmRole } from "@/types/auth/RealmRole";

export function useRequireRole(role: RealmRole) {
  const router = useRouter();
  const { status } = useAuth();
  const hasRole = useHasRole(role);

  useEffect(() => {
    if (status === "AUTH" && !hasRole) {
      router.replace("/access-denied");
    }
  }, [status, hasRole, router]);

  return hasRole;
}
