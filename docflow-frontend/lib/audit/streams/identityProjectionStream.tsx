import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { IdentityProjectionAuditRow } from "@/types/api/IdentityProjectionAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

type IdentityProjectionFilters = {
  correlationId: string;
  subjectId: string;
};

export const identityProjectionStream: AuditStreamDefinition<
  IdentityProjectionAuditRow,
  IdentityProjectionFilters
> = {
  key: "identity-projection",
  title: "Identity Projection Audit",
  endpoint: "/api/audit/identity-projection",
  filenameBase: "identity-projection-audit",
  filterDefinitions: [
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],
  columns: [
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<IdentityProjectionAuditRow, IdentityProjectionFilters>(
      "subjectId",
      "subjectId",
      (r) => r.subjectId,
      "font-mono",
    ),
    {
      header: "result",
      render: (r) => (
        <ResultBadge result={normalizeAuditResult(String(r.result))} />
      ),
    },
    {
      header: "reason",
      render: (r) => r.reasonCode ?? "",
    },
    pivotColumn<IdentityProjectionAuditRow, IdentityProjectionFilters>(
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
