"use client";

import { toast } from "sonner";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";

import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

import type { AdminTenant } from "@/types/admin/Tenant";

import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import FormField from "@/components/ui/FormField";
import Button from "../ui/Button";
import { TENANT_ROLES } from "@/types/invites/types";

type Props = {
  tenants: AdminTenant[];
};

const schema = z.object({
  tenantId: z.string().min(1, "Tenant is required"),
  email: z.email("Invalid email"),
  firstName: z.string().min(1, "First name is required"),
  lastName: z.string().min(1, "Last name is required"),
  jobTitle: z.string().optional(),
  department: z.string().optional(),
  tenantRole: z.enum(TENANT_ROLES),
});

type FormValues = z.infer<typeof schema>;

export default function InviteCreateCard({ tenants }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isValid },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: {
      tenantId: "",
      email: "",
      firstName: "",
      lastName: "",
      jobTitle: "",
      department: "",
      tenantRole: "MEMBER",
    },
  });

  const onSubmit = async (values: FormValues) => {
    try {
      await apiFetch<void>(`/api/tenants/${values.tenantId}/invites`, {
        method: "POST",
        body: JSON.stringify({
          email: values.email,
          firstName: values.firstName,
          lastName: values.lastName,
          jobTitle: values.jobTitle || null,
          department: values.department || null,
          tenantRole: values.tenantRole,
        }),
      });

      toast.success("Invite sent successfully.");
      reset();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          toast.error(
            "An invitation already exists for this email or the user already belongs to this tenant.",
          );
        } else {
          toast.error(err.message);
        }
      } else if (err instanceof Error) {
        toast.error(err.message);
      } else {
        toast.error("Unexpected error occurred.");
      }
    }
  };

  return (
    <Card>
      <div className="mb-4">
        <h2 className="text-lg font-semibold text-(--color-text-primary)">
          Create invite
        </h2>

        <p className="mt-1 text-sm text-(--color-text-secondary)">
          Select a tenant, then send a tenant-scoped invite to a new user.
        </p>
      </div>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="grid gap-4 md:grid-cols-2 xl:grid-cols-3"
      >
        <FormField label="Tenant" error={errors.tenantId?.message}>
          <Select {...register("tenantId")}>
            <option value="">Select a tenant</option>
            {tenants.map((tenant) => (
              <option key={tenant.id} value={tenant.id}>
                {tenant.name}
              </option>
            ))}
          </Select>
        </FormField>

        <FormField label="Email" error={errors.email?.message}>
          <Input type="email" {...register("email")} />
        </FormField>

        <FormField label="First name" error={errors.firstName?.message}>
          <Input {...register("firstName")} />
        </FormField>

        <FormField label="Last name" error={errors.lastName?.message}>
          <Input {...register("lastName")} />
        </FormField>

        <FormField label="Job title">
          <Input {...register("jobTitle")} />
        </FormField>

        <FormField label="Department">
          <Input {...register("department")} />
        </FormField>

        <FormField label="Tenant role" error={errors.tenantRole?.message}>
          <Select {...register("tenantRole")}>
            {TENANT_ROLES.map((role) => (
              <option key={role} value={role}>
                {role}
              </option>
            ))}
          </Select>
        </FormField>

        <div className="col-span-full flex justify-end pt-2">
          <Button
            type="submit"
            loading={isSubmitting}
            disabled={isSubmitting || !isValid}
          >
            Send Invite
          </Button>
        </div>
      </form>
    </Card>
  );
}
