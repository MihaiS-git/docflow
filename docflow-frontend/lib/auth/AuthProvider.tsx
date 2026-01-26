"use client";

import { fetchIdentity, fetchLocalUser, login as startLogin, logout as startLogout } from "@/lib/auth";
import { setAuthErrorHandler } from "@/lib/authEvents";
import { ForbiddenError } from "@/lib/apiErrors";
import { AuthStatus } from "@/types/auth/AuthStatus";
import { AuthUser } from "@/types/auth/AuthUser";
import { LocalUser } from "@/types/auth/LocalUser";
import { createContext, useCallback, useEffect, useMemo, useReducer } from "react";

type State = {
  status: AuthStatus;
  identity: AuthUser | null;
  localUser: LocalUser | null;
  blockedCode: string | null;
};

type Action =
  | { type: "BOOTSTRAP_START" }
  | { type: "AUTH_OK"; identity: AuthUser; localUser: LocalUser | null }
  | { type: "ANON" }
  | { type: "BLOCKED"; blockedCode: string };

const initialState: State = {
  status: "ANON",
  identity: null,
  localUser: null,
  blockedCode: null,
};

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "BOOTSTRAP_START":
      return { ...state, status: "LOADING" };

    case "AUTH_OK":
      return {
        status: "AUTH",
        identity: action.identity,
        localUser: action.localUser,
        blockedCode: null,
      };

    case "BLOCKED":
      return {
        status: "BLOCKED",
        identity: null,
        localUser: null,
        blockedCode: action.blockedCode,
      };

    case "ANON":
      return { ...initialState, status: "ANON" };

    default:
      return state;
  }
}

type AuthContextValue = {
  status: AuthStatus;
  isAuthenticated: boolean;
  identity: AuthUser | null;
  localUser: LocalUser | null;
  blockedCode: string | null;
  login: () => void;
  logout: () => void;
  refresh: () => Promise<void>;
  clearBlocked: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

const SESSION_FLAG = "docflow:auth:hasSession";
const LOGIN_INTENT_FLAG = "docflow:auth:loginIntent";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const toAnon = useCallback(() => {
    try {
      sessionStorage.removeItem(SESSION_FLAG);
      sessionStorage.removeItem(LOGIN_INTENT_FLAG);
    } catch {
      // ignore
    }
    dispatch({ type: "ANON" });
  }, []);

  const toBlocked = useCallback((blockedCode: string) => {
    try {
      sessionStorage.removeItem(SESSION_FLAG);
      sessionStorage.removeItem(LOGIN_INTENT_FLAG);
    } catch {
      // ignore
    }
    dispatch({ type: "BLOCKED", blockedCode });
  }, []);

  // Global reaction to 401/403 from apiFetch
  useEffect(() => {
    setAuthErrorHandler((event) => {
      if (event.type === "401") {
        toAnon();
      } else {
        toBlocked(event.errorCode);
      }
    });
    return () => setAuthErrorHandler(null);
  }, [toAnon, toBlocked]);

  const refresh = useCallback(async () => {
    dispatch({ type: "BOOTSTRAP_START" });

    try {
      const identity = await fetchIdentity();

      let localUser: LocalUser | null = null;
      try {
        localUser = await fetchLocalUser();
      } catch (e) {
        // CRITICAL: if backend says "USER_LOCKED" etc, preserve it
        if (e instanceof ForbiddenError) {
          toBlocked(e.errorCode);
          return;
        }
        localUser = null;
      }

      try {
        sessionStorage.setItem(SESSION_FLAG, "1");
        sessionStorage.removeItem(LOGIN_INTENT_FLAG);
      } catch {
        // ignore
      }

      dispatch({ type: "AUTH_OK", identity, localUser });
    } catch {
      // If identity fails because of a 401/403, apiFetch already emitted event.
      // Fall back to ANON for other errors.
      toAnon();
    }
  }, [toAnon, toBlocked]);

  useEffect(() => {
    let shouldTry = false;
    try {
      shouldTry =
        sessionStorage.getItem(LOGIN_INTENT_FLAG) === "1" ||
        sessionStorage.getItem(SESSION_FLAG) === "1";
    } catch {
      shouldTry = false;
    }
    if (shouldTry) {
      void refresh();
    }
  }, [refresh]);

  const login = useCallback(() => {
    try {
      sessionStorage.setItem(LOGIN_INTENT_FLAG, "1");
    } catch {
      // ignore
    }
    startLogin();
  }, []);

  const logout = useCallback(() => {
    toAnon();
    void startLogout();
  }, [toAnon]);

  const clearBlocked = useCallback(() => {
    dispatch({ type: "ANON" });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status: state.status,
      isAuthenticated: state.status === "AUTH",
      identity: state.identity,
      localUser: state.localUser,
      blockedCode: state.blockedCode,
      login,
      logout,
      refresh,
      clearBlocked,
    }),
    [state, login, logout, refresh, clearBlocked],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
