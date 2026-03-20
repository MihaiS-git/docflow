"use client";

import PageContainer from "@/components/layout/PageContainer";
import { IdentityProjectionAuditClient } from "./IdentityProjectionAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function IdentityProjectionAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Identity Projection"
        description="Trace how identities are derived, transformed, and propagated across system boundaries."
      />

      <IdentityProjectionAuditClient />
    </PageContainer>
  );
}