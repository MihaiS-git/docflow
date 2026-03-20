"use client";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import { AuditExportSnapshotsClient } from "./AuditExportSnapshotsClient";

export default function AuditExportSnapshotsPage() {
  return (
    <PageContainer>
      <PageHeader
        title="Audit — Exported Snapshots"
        description="Inspect exported audit snapshots and verify integrity using signed metadata."
      />

      <AuditExportSnapshotsClient />
    </PageContainer>
  );
}