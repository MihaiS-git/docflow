import type { OnboardingAuditRow } from "@/types/api/OnboardingAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";

import { timestampColumn } from "@/components/audit/columns/timestampColumn";
import { textColumn } from "@/components/audit/columns/textColumn";
import { badgeColumn } from "@/components/audit/columns/badgeColumn";
import { pivotColumn } from "@/components/audit/columns/pivotColumn";

type OnboardingFilters = {
  correlationId: string;
  subjectId: string;
  result: string;
};

export const onboardingStream: AuditStreamDefinition<
  OnboardingAuditRow,
  OnboardingFilters
> = {
  key: "onboarding",
  title: "Onboarding Audit",
  endpoint: "/api/audit/onboarding",
  filenameBase: "onboarding-audit",

  filterDefinitions: [
    { key: "subjectId", label: "Subject ID" },
    { key: "correlationId", label: "Correlation ID" },
  ],

  columns: [
    timestampColumn("timestamp", (r) => r.timestamp),

    pivotColumn("subjectId", "subjectId", (r) => r.subjectId, "font-mono"),

    textColumn("actor", (r) => r.actorUserId),
    textColumn("tenant", (r) => r.tenantId),
    textColumn("invite", (r) => r.inviteId),
    textColumn("outcome", (r) => r.outcome),

    badgeColumn("result", (r) => String(r.result)),

    textColumn("ip", (r) => r.ip),

    pivotColumn(
      "correlationId",
      "correlationId",
      (r) => r.correlationId,
      "font-mono",
    ),

    textColumn("fingerprint", (r) => r.eventFingerprint, "font-mono"),
  ],
};