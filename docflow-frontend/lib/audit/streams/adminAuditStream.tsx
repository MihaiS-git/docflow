import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { AdminAuditRow } from "@/types/api/AdminAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

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
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    {
      header: "action",
      render: (r) => r.actionType,
    },
    pivotColumn<AdminAuditRow, AdminAuditFilters>(
      "actor",
      "actorUserId",
      (r) => r.actorUserId,
      "font-mono"
    ),
    pivotColumn<AdminAuditRow, AdminAuditFilters>(
      "tenant",
      "tenantId",
      (r) => r.tenantId,
      "font-mono"
    ),
    {
      header: "target",
      render: (r) => r.targetUserId ?? "",
    },
    {
      header: "subject",
      render: (r) => r.subjectId,
    },
    {
      header: "result",
      render: (r) => (
        <ResultBadge result={normalizeAuditResult(String(r.result))} />
      ),
    },
    pivotColumn<AdminAuditRow, AdminAuditFilters>(
      "correlationId",
      "correlationId",
      (r) => r.correlationId,
      "font-mono"
    ),
    {
      header: "ip",
      render: (r) => r.ip,
    },
    {
      header: "fingerprint",
      render: (r) => r.eventFingerprint,
    },
  ],
};
