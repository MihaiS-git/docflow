import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { CredentialLifecycleAuditRow } from "@/types/api/CredentialLifecycleAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

type CredentialLifecycleFilters = {
  correlationId: string;
  subjectExternalId: string;
  result: string;
};

export const credentialLifecycleStream: AuditStreamDefinition<
  CredentialLifecycleAuditRow,
  CredentialLifecycleFilters
> = {
  key: "credential-lifecycle",
  title: "Credential Lifecycle Audit",
  endpoint: "/api/audit/credential-lifecycle",
  filenameBase: "credential-lifecycle-audit",
  filterDefinitions: [
    { key: "subjectExternalId", label: "Subject External ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],
  columns: [
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<CredentialLifecycleAuditRow, CredentialLifecycleFilters>(
      "subjectExternalId",
      "subjectExternalId",
      (r) => r.subjectExternalId,
      "font-mono",
    ),
    {
      header: "eventType",
      render: (r) => r.eventType,
    },
    {
      header: "requiredAction",
      render: (r) => r.requiredAction ?? "",
    },
    {
      header: "result",
      render: (r) => (
        <ResultBadge result={normalizeAuditResult(String(r.result))} />
      ),
    },
    {
      header: "ip",
      render: (r) => r.ip ?? "",
    },
    pivotColumn<CredentialLifecycleAuditRow, CredentialLifecycleFilters>(
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
