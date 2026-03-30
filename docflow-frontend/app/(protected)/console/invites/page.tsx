"use client";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import InviteCreateCard from "@/components/invites/InviteCreateCard";
import InvitesTableCard from "@/components/invites/InvitesTableCard";
import { useManagedTenantsQuery } from "@/hooks/admin/useManagedTenantsQuery";
import { useHasRole } from "@/lib/auth/useHasRole";
import { REALM_ROLES } from "@/types/auth/RealmRole";
import { useAuth } from "@/lib/auth/useAuth";

export default function InvitesPage() {
  const { status } = useAuth();
  const isAdmin = useHasRole(REALM_ROLES.ADMIN);

  const { data: tenants = [], error } = useManagedTenantsQuery(
    status === "AUTH",
  );

  if (status !== "AUTH") {
    return null;
  }
  
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
