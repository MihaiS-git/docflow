import type { IdentityProjectionAuditRow } from "@/types/api/IdentityProjectionAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

type IdentityProjectionFilters = {
  correlationId: string;
  subjectId: string;
};

export const identityProjectionStream: AuditStreamDefinition<
  IdentityProjectionAuditRow,
  IdentityProjectionFilters
> = {
  key: "identity-projection",
  title: "Identity Projection Audit",
  endpoint: "/api/audit/identity-projection",
  filenameBase: "identity-projection-audit",

  filterDefinitions: [
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],

  columns: [
    timestampColumn("timestamp", r => r.timestamp),

    pivotColumn("subjectId", "subjectId", r => r.subjectId, "font-mono"),

    badgeColumn("result", r => String(r.result)),

    textColumn("reason", r => r.reasonCode),

    pivotColumn("correlationId", "correlationId", r => r.correlationId, "font-mono"),
  ],
};