import type { AdminAuditRow } from "@/types/api/AdminAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
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
    { key: "correlationId", label: "Correlation ID" },
    { key: "actorUserId", label: "Actor User ID" },
    { key: "tenantId", label: "Tenant ID" },
  ],
  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),
    textColumn("action", (r) => r.actionType),
    pivotColumn("actor", "actorUserId", (r) => r.actorUserId),
    pivotColumn("tenant", "tenantId", (r) => r.tenantId),
    textColumn("target", (r) => r.targetUserId),
    textColumn("subject", (r) => r.subjectId),
    badgeColumn("result", (r) => String(r.result)),
    pivotColumn("correlationId", "correlationId", (r) => r.correlationId),
    textColumn("ip", (r) => r.ip),
    textColumn("fingerprint", (r) => r.eventFingerprint, "font-mono"),
  ],
};
