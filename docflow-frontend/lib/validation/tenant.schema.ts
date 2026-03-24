import { z } from "zod";

export const tenantSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Tenant name is required")
    .max(128, "Max 128 characters"),

  description: z
    .string()
    .trim()
    .max(1000, "Max 1000 characters")
    .optional()
    .or(z.literal("")),

  dataRegion: z
    .string()
    .trim()
    .max(128, "Max 128 characters")
    .optional()
    .or(z.literal("")),

  retentionDays: z
    .string()
    .optional()
    .refine((val) => !val || /^\d+$/.test(val), "Must be a whole number")
    .refine((val) => {
      if (!val) return true;
      const days = Number(val);
      return days === 0 || (days >= 30 && days <= 3650);
    }, "Must be 0 (unlimited) or between 30 and 3650 days"),
});

export type TenantFormValues = z.infer<typeof tenantSchema>;