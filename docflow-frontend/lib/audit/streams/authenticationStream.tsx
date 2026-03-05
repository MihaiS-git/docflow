import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { AuthenticationAuditRow } from "@/types/api/AuthenticationAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

type AuthenticationFilters = {
  correlationId: string;
  username: string;
  subjectId: string;
  result: string;
};

export const authenticationStream: AuditStreamDefinition<
  AuthenticationAuditRow,
  AuthenticationFilters
> = {
  key: "authentication",
  title: "Authentication Audit",
  endpoint: "/api/audit/authentication",
  filenameBase: "authentication-audit",
  filterDefinitions: [
    { key: "username", label: "Username" },
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],
  columns: [
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<AuthenticationAuditRow, AuthenticationFilters>(
      "username",
      "username",
      (r) => r.username,
    ),
    pivotColumn<AuthenticationAuditRow, AuthenticationFilters>(
      "subjectId",
      "subjectId",
      (r) => r.subjectId,
      "font-mono",
    ),
    {
      header: "source",
      render: (r) => r.source,
    },
    {
      header: "authResult",
      render: (r) => r.authenticationResult,
    },
    {
      header: "auditResult",
      render: (r) => <ResultBadge result={normalizeAuditResult(r.result)} />,
    },
    {
      header: "ip",
      render: (r) => r.ip,
    },
    pivotColumn<AuthenticationAuditRow, AuthenticationFilters>(
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
