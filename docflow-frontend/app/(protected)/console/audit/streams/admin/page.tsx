"use client";

import PageContainer from "@/components/layout/PageContainer";
import { AdminAuditClient } from "./AdminAuditClient";
import PageHeader from "@/components/layout/PageHeader";

export default function AdminAuditPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Admin Actions"
        description="Trace administrative actions and system-level changes across the platform."
      />

      <AdminAuditClient />
    </PageContainer>
  );
}