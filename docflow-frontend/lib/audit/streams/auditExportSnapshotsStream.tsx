import type { AuditExportSnapshotDTO } from "@/types/api/AuditExportSnapshotDTO";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";
import Button from "@/components/ui/Button";

type SnapshotFilters = {
  snapshotId: string;
  stream: string;
};

export const auditExportSnapshotsStream: AuditStreamDefinition<
  AuditExportSnapshotDTO,
  SnapshotFilters
> = {
  key: "audit-export-snapshots",
  title: "Audit Export Snapshots",
  endpoint: "/api/audit/exports",
  filenameBase: "audit-export-snapshots",

  filterDefinitions: [
    { key: "snapshotId", label: "Snapshot ID" },
    { key: "stream", label: "Stream" },
  ],

  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),

    pivotColumn("snapshotId", "snapshotId", (r) => r.id, "font-mono w-[220px]"),
    pivotColumn("stream", "stream", (r) => r.stream, "w-[160px]"),

    textColumn("rowCount", (r) => String(r.rowCount)),

    textColumn("createdBy", (r) => r.createdBy ?? "—", "font-mono"),

    textColumn(
      "range",
      (r) =>
        `${r.fromTs?.slice(0, 19) ?? "—"} → ${r.toTs?.slice(0, 19) ?? "—"}`,
      "font-mono text-xs"
    ),

    textColumn(
      "digest",
      (r) => r.sha256DigestHex?.slice(0, 10) ?? "—",
      "font-mono text-xs opacity-70"
    ),

    {
      header: "actions",
      render: (r) => (
        <Button
          variant="outline"
          size="sm"
          onClick={(e) => {
            e.stopPropagation();

            // ✅ dispatch event instead of using ctx / props
            window.dispatchEvent(
              new CustomEvent("audit:snapshot:verify", {
                detail: r,
              })
            );
          }}
        >
          Verify
        </Button>
      ),
    },
  ],
};