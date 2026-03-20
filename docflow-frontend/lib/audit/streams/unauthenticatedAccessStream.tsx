import type { UnauthenticatedAccessAuditRow } from "@/types/api/UnauthenticatedAccessAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

type UnauthenticatedAccessFilters = {
  correlationId: string;
};

export const unauthenticatedAccessStream: AuditStreamDefinition<
  UnauthenticatedAccessAuditRow,
  UnauthenticatedAccessFilters
> = {
  key: "unauthenticated-access",
  title: "Unauthenticated Access Audit",
  endpoint: "/api/audit/unauthenticated-access",
  filenameBase: "unauthenticated-access-audit",

  filterDefinitions: [{ key: "correlationId", label: "Correlation ID" }],

  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),

    textColumn("method", (r) => r.httpMethod ?? "-"),
    textColumn("path", (r) => r.path ?? "-"),
    textColumn("ip", (r) => r.ip ?? "-"),

    badgeColumn("result", (r) => String(r.result)),

    pivotColumn(
      "correlationId",
      "correlationId",
      (r) => r.correlationId,
      "font-mono",
    ),
  ],
};