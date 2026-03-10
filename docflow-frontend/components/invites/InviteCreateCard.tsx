"use client";

import type { FormEvent } from "react";
import type { AdminTenant } from "@/types/admin/Tenant";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import FormField from "@/components/ui/FormField";
import type { TenantRole } from "@/types/invites/types";

type InviteCreateCardProps = {
  tenants: AdminTenant[];
  selectedTenantId: string;
  onSelectedTenantIdChange: (value: string) => void;
  email: string;
  onEmailChange: (value: string) => void;
  firstName: string;
  onFirstNameChange: (value: string) => void;
  lastName: string;
  onLastNameChange: (value: string) => void;
  jobTitle: string;
  onJobTitleChange: (value: string) => void;
  department: string;
  onDepartmentChange: (value: string) => void;
  tenantRole: TenantRole;
  onTenantRoleChange: (value: TenantRole) => void;
  loading: boolean;
  success: string | null;
  error: string | null;
  onSubmit: (e: FormEvent<HTMLFormElement>) => void;
};

export default function InviteCreateCard({
  tenants,
  selectedTenantId,
  onSelectedTenantIdChange,
  email,
  onEmailChange,
  firstName,
  onFirstNameChange,
  lastName,
  onLastNameChange,
  jobTitle,
  onJobTitleChange,
  department,
  onDepartmentChange,
  tenantRole,
  onTenantRoleChange,
  loading,
  success,
  error,
  onSubmit,
}: InviteCreateCardProps) {
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

      <form onSubmit={onSubmit} className="space-y-4">
        <FormField label="Tenant">
          <Select
            required
            value={selectedTenantId}
            onChange={(e) => onSelectedTenantIdChange(e.target.value)}
          >
            <option value="" disabled>
              Select a tenant
            </option>

            {tenants.map((tenant) => (
              <option key={tenant.id} value={tenant.id}>
                {tenant.name}
              </option>
            ))}
          </Select>
        </FormField>

        <FormField label="Email">
          <Input
            required
            type="email"
            value={email}
            onChange={(e) => onEmailChange(e.target.value)}
          />
        </FormField>

        <FormField label="First name">
          <Input
            required
            value={firstName}
            onChange={(e) => onFirstNameChange(e.target.value)}
          />
        </FormField>

        <FormField label="Last name">
          <Input
            required
            value={lastName}
            onChange={(e) => onLastNameChange(e.target.value)}
          />
        </FormField>

        <FormField label="Job title">
          <Input
            value={jobTitle}
            onChange={(e) => onJobTitleChange(e.target.value)}
          />
        </FormField>

        <FormField label="Department">
          <Input
            value={department}
            onChange={(e) => onDepartmentChange(e.target.value)}
          />
        </FormField>

        <FormField label="Tenant role">
          <Select
            value={tenantRole}
            onChange={(e) => onTenantRoleChange(e.target.value as TenantRole)}
          >
            <option value="MEMBER">Member</option>
            <option value="EXECUTOR">Executor</option>
            <option value="REVIEWER">Reviewer</option>
            <option value="MANAGER">Manager</option>
          </Select>
        </FormField>

        <div className="pt-2">
          <button
            type="submit"
            disabled={loading || !selectedTenantId}
            className="rounded-md bg-(--color-primary) px-4 py-2 text-(--color-text-inverse) hover:opacity-90 disabled:opacity-50"
          >
            {loading ? "Sending…" : "Send Invite"}
          </button>
        </div>
      </form>

      {success && (
        <p className="mt-4 text-sm text-(--color-success)">{success}</p>
      )}

      {error && <p className="mt-4 text-sm text-(--color-error)">{error}</p>}
    </Card>
  );
}