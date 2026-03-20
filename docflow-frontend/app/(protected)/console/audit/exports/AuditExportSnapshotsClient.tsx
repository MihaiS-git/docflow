"use client";

import { useEffect, useState } from "react";
import { createAuditStreamClient } from "@/components/audit/createAuditStreamClient";
import { auditExportSnapshotsStream } from "@/lib/audit/streams/auditExportSnapshotsStream";

import type { AuditExportSnapshotDTO } from "@/types/api/AuditExportSnapshotDTO";
import { AuditExportVerificationModal } from "./AuditExportVerificationModal";

const BaseClient = createAuditStreamClient(auditExportSnapshotsStream);

export function AuditExportSnapshotsClient() {
  const [snapshot, setSnapshot] =
    useState<AuditExportSnapshotDTO | null>(null);

  useEffect(() => {
    function handler(e: Event) {
      const custom = e as CustomEvent<AuditExportSnapshotDTO>;
      setSnapshot(custom.detail);
    }

    window.addEventListener("audit:snapshot:verify", handler);

    return () => {
      window.removeEventListener("audit:snapshot:verify", handler);
    };
  }, []);

  return (
    <>
      <BaseClient />

      <AuditExportVerificationModal
        open={!!snapshot}
        snapshot={snapshot}
        onClose={() => setSnapshot(null)}
      />
    </>
  );
}