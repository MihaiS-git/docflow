import type { SensitiveAccessAuditRow } from "@/types/api/SensitiveAccessAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

type SensitiveAccessFilters = {
  correlationId: string;
  subjectId: string;
  tenantId: string;
  actorUserId: string;
};

export const sensitiveAccessStream: AuditStreamDefinition<
  SensitiveAccessAuditRow,
  SensitiveAccessFilters
> = {
  key: "sensitive-access",
  title: "Sensitive Access Audit",
  endpoint: "/api/audit/sensitive-access",
  filenameBase: "sensitive-access-audit",

  filterDefinitions: [
    { key: "tenantId", label: "Tenant ID" },
    { key: "actorUserId", label: "Actor User ID" },
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],

  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),

    pivotColumn("tenantId", "tenantId", (r) => r.tenantId, "font-mono"),
    pivotColumn("actorUserId", "actorUserId", (r) => r.actorUserId, "font-mono"),
    pivotColumn("subjectId", "subjectId", (r) => r.subjectId, "font-mono"),

    textColumn("resource", (r) => r.resourcePath ?? r.resource ?? "-"),
    textColumn("action", (r) => r.action),
    textColumn("classification", (r) => r.dataClassification),

    badgeColumn("result", (r) => String(r.result)),

    pivotColumn(
      "correlationId",
      "correlationId",
      (r) => r.correlationId,
      "font-mono",
    ),
  ],
};