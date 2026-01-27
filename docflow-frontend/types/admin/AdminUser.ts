import { UserStatus } from "../auth/UserStatus";

export type AdminUser = {
  id: string;
  email: string;
  status: UserStatus;
  roles: string[];
};
