"use client";

type Props = {
  onExportJsonl: () => void;
  onExportCsv: () => void;
  loading: "jsonl" | "csv" | null;
};

export function AuditExportButtons({
  onExportJsonl,
  onExportCsv,
  loading,
}: Props) {
  return (
    <div className="flex gap-2 flex-wrap">
      <button
        type="button"
        onClick={onExportJsonl}
        disabled={loading !== null}
        className="px-3 py-1 rounded border disabled:opacity-50"
      >
        {loading === "jsonl" ? "Exporting…" : "Export JSONL"}
      </button>

      <button
        type="button"
        onClick={onExportCsv}
        disabled={loading !== null}
        className="px-3 py-1 rounded border disabled:opacity-50"
      >
        {loading === "csv" ? "Exporting…" : "Export CSV"}
      </button>
    </div>
  );
}
