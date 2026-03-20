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
  useRef,
} from "react";

type State = {
  status: AuthStatus;
  identity: AuthUser | null;
  localUser: LocalUser | null;
  blockedCode: string | null;
};

type Action =
  | { type: "LOADING" }
  | { type: "BOOTSTRAP_READY"; identity: AuthUser }
  | { type: "AUTH_OK"; identity: AuthUser; localUser: LocalUser }
  | { type: "BLOCKED"; blockedCode: string }
  | { type: "ANON" };

const initialState: State = {
  status: "ANON",
  identity: null,
  localUser: null,
  blockedCode: null,
};

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "LOADING":
      if (state.status === "LOADING") return state;
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
      return initialState;

    default:
      return state;
  }
}

const SESSION_FLAG = "docflow:auth:hasSession";
const LOGIN_INTENT_FLAG = "docflow:auth:loginIntent";

type AuthStateContextValue = State;

type AuthActionsContextValue = {
  login: () => void;
  logout: () => void;
  refresh: () => Promise<void>;
  clearBlocked: () => void;
};

export const AuthStateContext = createContext<AuthStateContextValue | null>(
  null,
);

export const AuthActionsContext = createContext<AuthActionsContextValue | null>(
  null,
);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const mountedRef = useRef(true);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
    };
  }, []);

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

  const refresh = useCallback(async () => {
    try {
      const identity = await fetchIdentity();

      if (!identity) {
        toAnon();
        return;
      }

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

      if (!mountedRef.current) return;

      if (!localUser) {
        dispatch({ type: "BOOTSTRAP_READY", identity });
        return;
      }

      try {
        sessionStorage.setItem(SESSION_FLAG, "1");
        sessionStorage.removeItem(LOGIN_INTENT_FLAG);
      } catch {}

      dispatch({
        type: "AUTH_OK",
        identity,
        localUser,
      });
    } catch (e) {
      if (e instanceof ForbiddenError) return;
      toAnon();
    }
  }, [toAnon, toBlocked]);

  useEffect(() => {
    dispatch({ type: "LOADING" });
    void refresh();
  }, [refresh]);

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

  const actions = useMemo<AuthActionsContextValue>(
    () => ({
      login,
      logout,
      refresh,
      clearBlocked,
    }),
    [login, logout, refresh, clearBlocked],
  );

  return (
    <AuthStateContext.Provider value={state}>
      <AuthActionsContext.Provider value={actions}>
        {children}
      </AuthActionsContext.Provider>
    </AuthStateContext.Provider>
  );
}
