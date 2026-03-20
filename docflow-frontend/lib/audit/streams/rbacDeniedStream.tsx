import type { RbacDeniedAuditRow } from "@/types/api/RbacDeniedAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

type RbacDeniedFilters = {
  correlationId: string;
  subjectId: string;
};

export const rbacDeniedStream: AuditStreamDefinition<
  RbacDeniedAuditRow,
  RbacDeniedFilters
> = {
  key: "rbac-denied",
  title: "RBAC Denied Audit",
  endpoint: "/api/audit/rbac-denied",
  filenameBase: "rbac-denied-audit",

  filterDefinitions: [
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],

  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),

    pivotColumn("subjectId", "subjectId", (r) => r.subjectId, "font-mono"),

    textColumn("method", (r) => r.httpMethod),
    textColumn("path", (r) => r.path),

    badgeColumn("result", (r) => String(r.result)),

    textColumn("ip", (r) => r.ip),

    pivotColumn(
      "correlationId",
      "correlationId",
      (r) => r.correlationId,
      "font-mono",
    ),
  ],
};