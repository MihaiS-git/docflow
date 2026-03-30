import { AuthUser } from "./AuthUser";
import { LocalUser } from "./LocalUser";

export type AuthState =
  | {
      status: "LOADING";
      identity: null;
      localUser: null;
      blockedCode: null;
    }
  | {
      status: "ANON";
      identity: null;
      localUser: null;
      blockedCode: null;
    }
  | {
      status: "BOOTSTRAP";
      identity: AuthUser;
      localUser: null;
      blockedCode: null;
    }
  | {
      status: "AUTH";
      identity: AuthUser;
      localUser: LocalUser;
      blockedCode: null;
    }
  | {
      status: "BLOCKED";
      identity: null;
      localUser: null;
      blockedCode: string;
    };