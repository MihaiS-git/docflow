import type { CredentialLifecycleAuditRow } from "@/types/api/CredentialLifecycleAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

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
    timestampColumn("timestamp", r => r.timestamp),

    pivotColumn("subjectExternalId", "subjectExternalId", r => r.subjectExternalId, "font-mono"),

    textColumn("eventType", r => r.eventType),
    textColumn("requiredAction", r => r.requiredAction),
    badgeColumn("result", r => String(r.result)),
    textColumn("ip", r => r.ip),

    pivotColumn("correlationId", "correlationId", r => r.correlationId, "font-mono"),

    textColumn("fingerprint", r => r.eventFingerprint, "font-mono"),
  ],
};