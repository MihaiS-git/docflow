"use client";

import PageContainer from "@/components/layout/PageContainer";
import { CredentialLifecycleAuditClient } from "./CredentialLifecycleAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function CredentialLifecycleAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Credential Lifecycle"
        description="Track issuance, rotation, revocation, and usage of credentials across the system."
      />

      <CredentialLifecycleAuditClient />
    </PageContainer>
  );
}