import type { AuditColumn } from "../AuditColumn";

export function textColumn<T>(
  header: string,
  value: (row: T) => string | number | null | undefined,
  className?: string
): AuditColumn<T> {
  return {
    header,
    className,
    render: (row) => {
      const v = value(row);
      return v === null || v === undefined ? "" : String(v);
    },
  };
}