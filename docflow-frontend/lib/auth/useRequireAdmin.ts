"use client";

import { useRequireRole } from "./useRequireRole";

export function useRequireAdmin() {
  return useRequireRole("ADMIN");
}