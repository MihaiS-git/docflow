import { AuthUser } from "./AuthUser";
import { LocalUser } from "./LocalUser";

export type AuthState = {
  isAuthenticated: boolean;
  identity: AuthUser | null;   // from /api/auth/me
  user: LocalUser | null;      // from /api/users/me
};