"use client";

import {
  fetchIdentity,
  fetchLocalUser,
  login as startLogin,
  logout as startLogout,
} from "@/lib/auth/auth";
import { setAuthErrorHandler } from "@/lib/auth/authEvents";
import { ForbiddenError } from "@/lib/apiErrors";
import { AuthStatus } from "@/types/auth/AuthStatus";
import { AuthUser } from "@/types/auth/AuthUser";
import { LocalUser } from "@/types/auth/LocalUser";
import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useReducer,
} from "react";

type State = {
  status: AuthStatus;
  identity: AuthUser | null;
  localUser: LocalUser | null;
  blockedCode: string | null;
};

type Action =
  | { type: "BOOTSTRAP_START" }
  | { type: "BOOTSTRAP_READY"; identity: AuthUser }
  | { type: "AUTH_OK"; identity: AuthUser; localUser: LocalUser }
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

    case "BOOTSTRAP_READY":
      return {
        status: "BOOTSTRAP",
        identity: action.identity,
        localUser: null,
        blockedCode: null,
      };

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
    } catch {}
    dispatch({ type: "ANON" });
  }, []);

  const toBlocked = useCallback((blockedCode: string) => {
    try {
      sessionStorage.removeItem(SESSION_FLAG);
      sessionStorage.removeItem(LOGIN_INTENT_FLAG);
    } catch {}
    dispatch({ type: "BLOCKED", blockedCode });
  }, []);

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

      let localUser: LocalUser | undefined;

      try {
        localUser = await fetchLocalUser();
      } catch (e) {
        if (e instanceof ForbiddenError) {
          toBlocked(e.errorCode);
          return;
        }
        throw e;
      }

      // ✅ 204 → undefined → BOOTSTRAP
      if (!localUser) {
        dispatch({ type: "BOOTSTRAP_READY", identity });
        return;
      }

      sessionStorage.setItem(SESSION_FLAG, "1");
      sessionStorage.removeItem(LOGIN_INTENT_FLAG);

      dispatch({ type: "AUTH_OK", identity, localUser });
    } catch (e) {
      if (e instanceof ForbiddenError) {
        return;
      }
      toAnon();
    }
  }, [toAnon, toBlocked]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const login = useCallback(() => {
    try {
      sessionStorage.setItem(LOGIN_INTENT_FLAG, "1");
    } catch {}
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
