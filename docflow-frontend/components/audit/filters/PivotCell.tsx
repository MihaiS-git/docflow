"use client";

type PivotCellProps<F extends Record<string, string>> = {
  value: string | null | undefined;
  filterKey: keyof F;
  setFilter: (key: keyof F, value: string) => void;
  triggerQuery?: () => void;
  className?: string;
};

export function PivotCell<F extends Record<string, string>>({
  value,
  filterKey,
  setFilter,
  triggerQuery,
  className,
}: PivotCellProps<F>) {
  if (!value) return <span>-</span>;

  return (
    <button
      type="button"
      title={`Filter by ${String(filterKey)}`}
      className={`font-mono text-blue-600 hover:underline cursor-pointer ${className ?? ""}`}
      onClick={() => {
        setFilter(filterKey, value);
        triggerQuery?.();
      }}
    >
      {value}
    </button>
  );
}