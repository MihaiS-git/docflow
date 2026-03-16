"use client";

import { useContext } from "react";
import {
  AuthStateContext,
  AuthActionsContext,
} from "@/lib/auth/AuthProvider";

export function useAuth() {
  const state = useContext(AuthStateContext);
  const actions = useContext(AuthActionsContext);

  if (!state || !actions) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return {
    ...state,
    isAuthenticated: state.status === "AUTH",
    ...actions,
  };
}