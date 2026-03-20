"use client";

import PageContainer from "@/components/layout/PageContainer";
import { LifecycleDeniedAuditClient } from "./LifecycleDeniedAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function LifecycleDeniedAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Lifecycle Denied"
        description="Inspect rejected lifecycle operations due to policy, validation, or state constraints."
      />

      <LifecycleDeniedAuditClient />
    </PageContainer>
  );
}