"use client";

import { useEffect } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

import Dialog from "@/components/ui/Dialog";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";

import type { AdminTenant } from "@/types/admin/Tenant";
import { updateTenant } from "@/lib/admin/adminTenants";
import {
  tenantSchema,
  type TenantFormValues,
} from "@/lib/validation/tenant.schema";

type Props = {
  tenant: AdminTenant;
  open: boolean;
  onClose: () => void;
};

export default function EditTenantDialog({
  tenant,
  open,
  onClose,
}: Props) {
  const queryClient = useQueryClient();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<TenantFormValues>({
    resolver: zodResolver(tenantSchema),
    defaultValues: {
      name: "",
      description: "",
      dataRegion: "",
      retentionDays: "",
    },
  });

  useEffect(() => {
    if (!open) return;

    reset({
      name: tenant.name ?? "",
      description: tenant.description ?? "",
      dataRegion: tenant.dataRegion ?? "",
      retentionDays:
        tenant.retentionDays != null ? String(tenant.retentionDays) : "",
    });
  }, [tenant, open, reset]);

  const updateMutation = useMutation({
    mutationFn: async (values: TenantFormValues) => {
      await updateTenant(tenant.id, {
        name: values.name.trim(),
        description: values.description?.trim() || undefined,
        dataRegion: values.dataRegion?.trim() || undefined,
        retentionDays:
          values.retentionDays && values.retentionDays !== ""
            ? Number(values.retentionDays)
            : undefined,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["tenants"] });
      onClose();
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    await updateMutation.mutateAsync(values);
  });

  return (
    <Dialog open={open} onClose={onClose} title="Edit tenant">
      <div className="space-y-4">
        <FormField label="Tenant name" error={errors.name?.message}>
          <Input {...register("name")} invalid={!!errors.name} />
        </FormField>

        <FormField label="Description" error={errors.description?.message}>
          <textarea
            {...register("description")}
            aria-invalid={!!errors.description}
            rows={3}
            className={`
              w-full
              resize-y
              rounded
              border ${errors.description ? "border-(--color-error)" : "border-(--color-border)"}
              bg-(--color-surface)
              px-2 py-2
              text-(--color-text-primary)
              focus:outline-none
              focus:ring-2
              ${errors.description ? "focus:ring-(--color-error)" : "focus:ring-(--color-primary)"}
            `}
          />
        </FormField>

        <FormField label="Data region" error={errors.dataRegion?.message}>
          <Input {...register("dataRegion")} invalid={!!errors.dataRegion} />
        </FormField>

        <FormField
          label="Retention days"
          error={errors.retentionDays?.message}
        >
          <Input
            type="number"
            {...register("retentionDays")}
            invalid={!!errors.retentionDays}
          />
        </FormField>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>

          <Button loading={updateMutation.isPending} onClick={onSubmit}>
            Save
          </Button>
        </div>
      </div>
    </Dialog>
  );
}