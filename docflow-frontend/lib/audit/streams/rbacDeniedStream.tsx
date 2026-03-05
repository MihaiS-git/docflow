import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { RbacDeniedAuditRow } from "@/types/api/RbacDeniedAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

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
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<RbacDeniedAuditRow, RbacDeniedFilters>(
      "subjectId",
      "subjectId",
      (r) => r.subjectId,
      "font-mono",
    ),
    {
      header: "method",
      render: (r) => r.httpMethod,
    },
    {
      header: "path",
      render: (r) => r.path,
    },
    {
      header: "result",
      render: (r) => (
        <ResultBadge result={normalizeAuditResult(String(r.result))} />
      ),
    },
    {
      header: "ip",
      render: (r) => r.ip,
    },
    pivotColumn<RbacDeniedAuditRow, RbacDeniedFilters>(
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
