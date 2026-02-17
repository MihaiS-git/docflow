"use client";

type Props = {
  from: string;
  to: string;
  size: number;
  onFromChange: (v: string) => void;
  onToChange: (v: string) => void;
  onSizeChange: (v: number) => void;
  onQuery: () => void;
  loading: boolean;
};

export function AuditRangePanel({
  from,
  to,
  size,
  onFromChange,
  onToChange,
  onSizeChange,
  onQuery,
  loading,
}: Props) {
  return (
    <div className="border rounded p-3 space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div className="space-y-1">
          <label className="text-sm font-medium">From</label>
          <input
            type="datetime-local"
            step={1}
            className="border rounded px-2 py-1 w-full"
            value={from}
            onChange={(e) => onFromChange(e.target.value)}
          />
        </div>

        <div className="space-y-1">
          <label className="text-sm font-medium">To</label>
          <input
            type="datetime-local"
            step={1}
            className="border rounded px-2 py-1 w-full"
            value={to}
            onChange={(e) => onToChange(e.target.value)}
          />
        </div>

        <div className="space-y-1">
          <label className="text-sm font-medium">Size</label>
          <input
            type="number"
            min={1}
            max={500}
            className="border rounded px-2 py-1 w-full"
            value={size}
            onChange={(e) => onSizeChange(Number(e.target.value))}
          />
        </div>
      </div>

      <button
        type="button"
        onClick={onQuery}
        disabled={loading}
        className="px-3 py-1 rounded border disabled:opacity-50"
      >
        {loading ? "Loading…" : "Query"}
      </button>
    </div>
  );
}
