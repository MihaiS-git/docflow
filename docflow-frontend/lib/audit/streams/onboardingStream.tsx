import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { OnboardingAuditRow } from "@/types/api/OnboardingAuditRow";
import type { AuditStreamDefinition } from "@/lib/audit/AuditStreamDefinition";
import { pivotColumn } from "@/components/audit/pivotColumn";

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
    {
      header: "timestamp",
      render: (r) => formatAuditTimestamp(r.timestamp),
    },
    pivotColumn<OnboardingAuditRow, OnboardingFilters>(
      "subjectId",
      "subjectId",
      (r) => r.subjectId,
      "font-mono",
    ),
    {
      header: "actor",
      render: (r) => r.actorUserId ?? "",
    },
    {
      header: "tenant",
      render: (r) => r.tenantId ?? "",
    },
    {
      header: "invite",
      render: (r) => r.inviteId ?? "",
    },
    {
      header: "outcome",
      render: (r) => r.outcome ?? "",
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
    pivotColumn<OnboardingAuditRow, OnboardingFilters>(
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
