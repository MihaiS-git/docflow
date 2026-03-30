"use client";

import { useContext } from "react";
import { AuthStateContext } from "@/lib/auth/AuthProvider";
import { AuthState } from "@/types/auth/AuthState";

export function useAuthSelector<T>(selector: (state: AuthState) => T): T {
  const state = useContext(AuthStateContext);

  if (!state) {
    throw new Error("useAuthSelector must be used inside AuthProvider");
  }

  return selector(state);
}
