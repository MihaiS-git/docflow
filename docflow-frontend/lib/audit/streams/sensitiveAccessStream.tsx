import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { SensitiveAccessAuditRow } from "@/types/api/SensitiveAccessAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

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
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<SensitiveAccessAuditRow, SensitiveAccessFilters>(
      "tenantId",
      "tenantId",
      (r) => r.tenantId,
      "font-mono",
    ),
    pivotColumn<SensitiveAccessAuditRow, SensitiveAccessFilters>(
      "actorUserId",
      "actorUserId",
      (r) => r.actorUserId,
      "font-mono",
    ),
    pivotColumn<SensitiveAccessAuditRow, SensitiveAccessFilters>(
      "subjectId",
      "subjectId",
      (r) => r.subjectId,
      "font-mono",
    ),
    {
      header: "resource",
      render: (r) => r.resourcePath ?? r.resource ?? "-",
    },
    {
      header: "action",
      render: (r) => r.action ?? "-",
    },
    {
      header: "classification",
      render: (r) => r.dataClassification ?? "-",
    },
    {
      header: "result",
      render: (r) => (
        <ResultBadge result={normalizeAuditResult(String(r.result))} />
      ),
    },
    pivotColumn<SensitiveAccessAuditRow, SensitiveAccessFilters>(
      "correlationId",
      "correlationId",
      (r) => r.correlationId,
      "font-mono",
    ),
    {
      header: "fingerprint",
      className: "font-mono",
      render: (r) => r.eventFingerprint,
    },
  ],
};
