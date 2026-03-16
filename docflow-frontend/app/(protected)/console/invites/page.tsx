"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth/useAuth";
import { fetchManagedTenants } from "@/lib/admin/adminTenants";

import type { AdminTenant } from "@/types/admin/Tenant";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import InviteCreateCard from "@/components/invites/InviteCreateCard";
import InvitesTableCard from "@/components/invites/InvitesTableCard";

export default function InvitesPage() {
  const { status, identity } = useAuth();
  const isAdmin = status === "AUTH" && identity?.roles.includes("ADMIN");

  const [tenants, setTenants] = useState<AdminTenant[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin) return;

    let active = true;

    (async () => {
      try {
        const result = await fetchManagedTenants();
        if (active) setTenants(result);
      } catch {
        if (active) setError("Failed to load tenants.");
      }
    })();

    return () => {
      active = false;
    };
  }, [isAdmin]);

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

      {error && <p className="mb-4 text-sm text-(--color-error)">{error}</p>}

      <div className="flex flex-col gap-6">
        <InviteCreateCard tenants={tenants} />
        <InvitesTableCard tenants={tenants} />
      </div>
    </PageContainer>
  );
}
