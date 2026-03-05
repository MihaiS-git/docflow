import { PivotCell } from "@/components/audit/filters/PivotCell";
import type { AuditColumn } from "./AuditColumn";

export function pivotColumn<
  T,
  F extends Record<string, string>
>(
  header: string,
  filterKey: keyof F,
  value: (row: T) => string | null | undefined,
  className?: string
): AuditColumn<T> {
  return {
    header,
    className,
    render: (row, ctx) => (
      <PivotCell
        value={value(row)}
        filterKey={filterKey}
        setFilter={ctx.setFilter as (key: keyof F, value: string) => void}
        triggerQuery={ctx.triggerQuery}
      />
    ),
  };
}