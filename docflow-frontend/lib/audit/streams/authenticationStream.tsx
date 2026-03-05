import type { AuthenticationAuditRow } from "@/types/api/AuthenticationAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

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
    timestampColumn("timestamp", r => r.timestamp),

    pivotColumn("username", "username", r => r.username),
    pivotColumn("subjectId", "subjectId", r => r.subjectId, "font-mono"),

    textColumn("source", r => r.source),
    textColumn("authResult", r => r.authenticationResult),

    badgeColumn("auditResult", r => r.result),

    textColumn("ip", r => r.ip),

    pivotColumn("correlationId", "correlationId", r => r.correlationId, "font-mono"),

    textColumn("fingerprint", r => r.eventFingerprint, "font-mono"),
  ],
};