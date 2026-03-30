import { z } from "zod";

export const RealmRoleSchema = z.enum(["ADMIN", "USER", "AUDITOR"]);

export const AuthUserSchema = z.object({
  username: z.string(),
  email: z.email().optional(),
  roles: z.array(RealmRoleSchema),
});