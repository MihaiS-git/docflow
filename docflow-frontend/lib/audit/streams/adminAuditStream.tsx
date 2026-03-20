import type { AdminAuditRow } from "@/types/api/AdminAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { isValidCorrelationId } from "@/lib/audit/validators";
import { pivotColumn } from "@/components/audit/pivotColumn";
import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";

type AdminAuditFilters = {
  correlationId: string;
  actorUserId: string;
  tenantId: string;
};

export const adminAuditStream: AuditStreamDefinition<
  AdminAuditRow,
  AdminAuditFilters
> = {
  key: "admin-actions",
  title: "Admin Actions Audit",
  endpoint: "/api/audit/admin-actions",
  filenameBase: "admin-actions",
  filterDefinitions: [
    {
      key: "correlationId",
      label: "Correlation ID",
      validate: isValidCorrelationId,
    },
    { key: "actorUserId", label: "Actor User ID" },
    { key: "tenantId", label: "Tenant ID" },
  ],
  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),
    textColumn("action", (r) => r.actionType),
    pivotColumn("actor", "actorUserId", (r) => r.actorUserId, "font-mono text-[11px]"),
    pivotColumn("tenant", "tenantId", (r) => r.tenantId, "font-mono text-[11px]"),
    textColumn("target", (r) => r.targetUserId, "font-mono text-[11px]"),
    textColumn("subject", (r) => r.subjectId, "font-mono text-[11px]"),
    badgeColumn("result", (r) => String(r.result)),
    pivotColumn("correlationId", "correlationId", (r) => r.correlationId, "font-mono text-[11px]"),
    textColumn("ip", (r) => r.ip),
  ],
};