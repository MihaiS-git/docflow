"use client";

import { useAuth } from "@/lib/auth/useAuth";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import InviteCreateCard from "@/components/invites/InviteCreateCard";
import InvitesTableCard from "@/components/invites/InvitesTableCard";
import { useManagedTenantsQuery } from "@/hooks/admin/useManagedTenantsQuery";

export default function InvitesPage() {
  const { status, identity } = useAuth();
  const isAdmin = status === "AUTH" && identity?.roles.includes("ADMIN");

  const { data: tenants = [], error } = useManagedTenantsQuery(
    isAdmin ?? false,
  );

  if (!isAdmin) {
    return (
      <PageContainer>
        <p className="text-(--color-text-secondary)">Access denied</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title="Tenant Invites"
        description="Create, search, revoke, and clean up tenant invites."
      />

      {error && (
        <p className="mb-4 text-sm text-(--color-error)">
          {(error as Error).message || "Failed to load tenants."}
        </p>
      )}

      <div className="flex flex-col gap-6">
        <InviteCreateCard tenants={tenants} />
        <InvitesTableCard tenants={tenants} />
      </div>
    </PageContainer>
  );
}
