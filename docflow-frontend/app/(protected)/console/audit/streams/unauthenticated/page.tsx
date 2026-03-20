"use client";

import PageContainer from "@/components/layout/PageContainer";
import { UnauthenticatedAccessAuditClient } from "./UnauthenticatedAccessAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function UnauthenticatedAccessAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Unauthenticated Access"
        description="Track access attempts performed without authentication, including blocked and invalid requests."
      />

      <UnauthenticatedAccessAuditClient />
    </PageContainer>
  );
}