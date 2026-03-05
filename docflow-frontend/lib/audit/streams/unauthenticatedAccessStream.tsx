import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { UnauthenticatedAccessAuditRow } from "@/types/api/UnauthenticatedAccessAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

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
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    {
      header: "method",
      render: (r) => r.httpMethod ?? "-",
    },
    {
      header: "path",
      render: (r) => r.path ?? "-",
    },
    {
      header: "ip",
      render: (r) => r.ip ?? "-",
    },
    {
      header: "result",
      render: (r) => (
        <ResultBadge result={normalizeAuditResult(String(r.result))} />
      ),
    },
    pivotColumn<UnauthenticatedAccessAuditRow, UnauthenticatedAccessFilters>(
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
