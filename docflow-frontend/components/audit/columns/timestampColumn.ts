import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import type { AuditColumn } from "../AuditColumn";

export function timestampColumn<T>(
  header: string,
  value: (row: T) => string
): AuditColumn<T> {
  return {
    header,
    render: (row) => formatAuditTimestamp(value(row)),
  };
}