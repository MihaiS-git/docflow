"use client";

import PageContainer from "@/components/layout/PageContainer";
import { RbacDeniedAuditClient } from "./RbacDeniedAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function RbacDeniedAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — RBAC Denied"
        description="Analyze authorization failures caused by missing roles or insufficient permissions."
      />

      <RbacDeniedAuditClient />
    </PageContainer>
  );
}