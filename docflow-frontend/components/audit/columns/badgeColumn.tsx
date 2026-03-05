import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";
import type { AuditColumn } from "../AuditColumn";

export function badgeColumn<T>(
  header: string,
  value: (row: T) => string
): AuditColumn<T> {
  return {
    header,
    render: (row) => (
      <ResultBadge result={normalizeAuditResult(value(row))} />
    ),
  };
}