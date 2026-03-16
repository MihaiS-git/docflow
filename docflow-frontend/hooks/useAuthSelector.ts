"use client";

import { useContext, useMemo } from "react";
import { AuthStateContext } from "@/lib/auth/AuthProvider";

export function useAuthSelector<T>(selector: (state: NonNullable<React.ContextType<typeof AuthStateContext>>) => T): T {
  const state = useContext(AuthStateContext);

  if (!state) {
    throw new Error("useAuthSelector must be used inside AuthProvider");
  }

  return useMemo(() => selector(state), [state, selector]);
}