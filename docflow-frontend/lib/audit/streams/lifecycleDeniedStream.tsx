import type { LifecycleDeniedAuditRow } from "@/types/api/LifecycleDeniedAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

type LifecycleDeniedFilters = {
  correlationId: string;
  subjectId: string;
};

export const lifecycleDeniedStream: AuditStreamDefinition<
  LifecycleDeniedAuditRow,
  LifecycleDeniedFilters
> = {
  key: "lifecycle-denied",
  title: "Lifecycle Denied Audit",
  endpoint: "/api/audit/lifecycle-denied",
  filenameBase: "lifecycle-denied-audit",

  filterDefinitions: [
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],

  columns: [
    timestampColumn("timestamp", r => r.timestamp),

    pivotColumn("subjectId", "subjectId", r => r.subjectId, "font-mono"),

    textColumn("reason", r => r.reasonCode),
    textColumn("method", r => r.httpMethod),
    textColumn("path", r => r.path),

    badgeColumn("result", r => String(r.result)),

    textColumn("ip", r => r.ip),

    pivotColumn("correlationId", "correlationId", r => r.correlationId, "font-mono"),
  ],
};