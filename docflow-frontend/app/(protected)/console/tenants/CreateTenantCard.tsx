"use client";

import { memo } from "react";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Button from "@/components/ui/Button";
import FormField from "@/components/ui/FormField";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  tenantSchema,
  type TenantFormValues,
} from "@/lib/validation/tenant.schema";

type Props = {
  loading: boolean;
  error: string | null;
  onSubmit: (data: {
    name: string;
    description?: string;
    dataRegion?: string;
    retentionDays?: number;
  }) => Promise<void> | void;
};

function CreateTenantCard({ loading, error, onSubmit }: Props) {
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<TenantFormValues>({
    resolver: zodResolver(tenantSchema),
    defaultValues: {
      name: "",
      description: "",
      dataRegion: "",
      retentionDays: "",
    },
  });

  const submit = handleSubmit(async (values) => {
    await onSubmit({
      name: values.name.trim(),
      description: values.description?.trim() || undefined,
      dataRegion: values.dataRegion?.trim() || undefined,
      retentionDays:
        values.retentionDays && values.retentionDays !== ""
          ? Number(values.retentionDays)
          : undefined,
    });

    reset();
  });

  return (
    <Card title="Create tenant">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-4">
        <FormField label="Tenant name" error={errors.name?.message}>
          <Input
            {...register("name")}
            invalid={!!errors.name}
            disabled={loading || isSubmitting}
          />
        </FormField>

        <FormField label="Description" error={errors.description?.message}>
          <Input
            {...register("description")}
            invalid={!!errors.description}
            disabled={loading || isSubmitting}
          />
        </FormField>

        <FormField label="Data region" error={errors.dataRegion?.message}>
          <Input
            {...register("dataRegion")}
            invalid={!!errors.dataRegion}
            disabled={loading || isSubmitting}
          />
        </FormField>

        <FormField
          label="Retention days"
          error={errors.retentionDays?.message}
        >
          <Input
            type="number"
            {...register("retentionDays")}
            invalid={!!errors.retentionDays}
            disabled={loading || isSubmitting}
          />
        </FormField>

        <div className="flex justify-end sm:col-span-4">
          <Button type="submit" loading={loading || isSubmitting}>
            Create
          </Button>
        </div>
      </form>

      {error && <p className="mt-3 text-sm text-(--color-error)">{error}</p>}
    </Card>
  );
}

export default memo(CreateTenantCard);