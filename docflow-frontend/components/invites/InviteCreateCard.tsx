"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";

import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";

import type { AdminTenant } from "@/types/admin/Tenant";
import type { TenantRole } from "@/types/invites/types";

import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import FormField from "@/components/ui/FormField";

type Props = {
  tenants: AdminTenant[];
};

export default function InviteCreateCard({ tenants }: Props) {
  const [selectedTenantId, setSelectedTenantId] = useState("");

  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [jobTitle, setJobTitle] = useState("");
  const [department, setDepartment] = useState("");
  const [tenantRole, setTenantRole] = useState<TenantRole>("MEMBER");

  const [loading, setLoading] = useState(false);

  useEffect(() => {}, []);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!selectedTenantId) {
      toast.error("Select a tenant first.");
      return;
    }

    setLoading(true);

    try {
      await apiFetch<void>(`/api/tenants/${selectedTenantId}/invites`, {
        method: "POST",
        body: JSON.stringify({
          email,
          firstName,
          lastName,
          jobTitle: jobTitle || null,
          department: department || null,
          tenantRole,
        }),
      });

      toast.success("Invite sent successfully.");

      setEmail("");
      setFirstName("");
      setLastName("");
      setJobTitle("");
      setDepartment("");
      setTenantRole("MEMBER");
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
    } finally {
      setLoading(false);
    }
  }

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
        onSubmit={onSubmit}
        className="grid gap-4 md:grid-cols-2 xl:grid-cols-3"
      >
        <FormField label="Tenant">
          <Select
            required
            value={selectedTenantId}
            onChange={(e) => setSelectedTenantId(e.target.value)}
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
            onChange={(e) => setEmail(e.target.value)}
          />
        </FormField>

        <FormField label="First name">
          <Input
            required
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
          />
        </FormField>

        <FormField label="Last name">
          <Input
            required
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
          />
        </FormField>

        <FormField label="Job title">
          <Input
            value={jobTitle}
            onChange={(e) => setJobTitle(e.target.value)}
          />
        </FormField>

        <FormField label="Department">
          <Input
            value={department}
            onChange={(e) => setDepartment(e.target.value)}
          />
        </FormField>

        <FormField label="Tenant role">
          <Select
            value={tenantRole}
            onChange={(e) => setTenantRole(e.target.value as TenantRole)}
          >
            <option value="MEMBER">Member</option>
            <option value="EXECUTOR">Executor</option>
            <option value="REVIEWER">Reviewer</option>
            <option value="MANAGER">Manager</option>
          </Select>
        </FormField>

        <div className="col-span-full flex justify-end pt-2">
          <button
            type="submit"
            disabled={loading || !selectedTenantId}
            className="rounded-md bg-(--color-primary) px-4 py-2 text-(--color-text-inverse) hover:opacity-90 disabled:opacity-50"
          >
            {loading ? "Sending…" : "Send Invite"}
          </button>
        </div>
      </form>
    </Card>
  );
}
