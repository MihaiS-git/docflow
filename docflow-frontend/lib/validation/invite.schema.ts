import { z } from "zod";
import { TENANT_ROLES } from "@/types/invites/types";

export const inviteSchema = z.object({
  tenantId: z.uuid("Invalid tenant"),

  email: z
    .string()
    .trim()
    .min(1, "Email is required")
    .pipe(z.email("Invalid email").max(254, "Max 254 characters")),
    
  firstName: z
    .string()
    .trim()
    .min(1, "First name is required")
    .max(255, "Max 255 characters"),

  lastName: z
    .string()
    .trim()
    .min(1, "Last name is required")
    .max(255, "Max 255 characters"),

  jobTitle: z
    .string()
    .trim()
    .max(255, "Max 255 characters")
    .optional()
    .or(z.literal("")),

  department: z
    .string()
    .trim()
    .max(255, "Max 255 characters")
    .optional()
    .or(z.literal("")),

  tenantRole: z.enum(TENANT_ROLES),
});

export type InviteFormValues = z.infer<typeof inviteSchema>;
