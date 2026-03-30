"use client";

import { useAuth } from "@/lib/auth/useAuth";
import { RealmRole } from "@/types/auth/RealmRole";

/**
 * Check if current authenticated user has a specific role.
 *
 * Safe:
 * - returns false if not authenticated
 * - no optional chaining needed in consumers
 */
export function useHasRole(role: RealmRole): boolean {
  const { status, identity } = useAuth();

  if (status !== "AUTH") return false;

  return identity.roles.includes(role);
}

/**
 * Optional helper for multiple roles (ANY match)
 */
export function useHasAnyRole(roles: RealmRole[]): boolean {
  const { status, identity } = useAuth();

  if (status !== "AUTH") return false;

  return roles.some((r) => identity.roles.includes(r));
}
