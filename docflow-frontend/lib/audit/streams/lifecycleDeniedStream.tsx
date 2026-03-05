import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { LifecycleDeniedAuditRow } from "@/types/api/LifecycleDeniedAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

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
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<LifecycleDeniedAuditRow, LifecycleDeniedFilters>(
      "subjectId",
      "subjectId",
      (r) => r.subjectId,
      "font-mono",
    ),
    {
      header: "reason",
      render: (r) => r.reasonCode,
    },
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
    pivotColumn<LifecycleDeniedAuditRow, LifecycleDeniedFilters>(
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
