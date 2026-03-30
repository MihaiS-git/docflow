export type AuthUnauthenticated = {
  status: "UNAUTH";
  identity: null;
};

export type AuthLoading = {
  status: "LOADING";
  identity: null;
};

export type AuthAuthenticated = {
  status: "AUTH";
  identity: {
    userId: string;
    email: string;
    roles: string[];
  };
};

export type AuthState =
  | AuthUnauthenticated
  | AuthLoading
  | AuthAuthenticated;