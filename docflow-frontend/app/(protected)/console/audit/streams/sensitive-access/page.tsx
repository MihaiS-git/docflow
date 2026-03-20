"use client";

import PageContainer from "@/components/layout/PageContainer";
import { SensitiveAccessAuditClient } from "./SensitiveAccessAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function SensitiveAccessAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Sensitive Access"
        description="Monitor access to sensitive data and high-risk operations requiring strict oversight."
      />

      <SensitiveAccessAuditClient />
    </PageContainer>
  );
}