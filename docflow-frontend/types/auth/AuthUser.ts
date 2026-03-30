import { RealmRole } from "./RealmRole";

export type AuthUser = {
  username: string;
  email?: string;
  roles: RealmRole[];
};
