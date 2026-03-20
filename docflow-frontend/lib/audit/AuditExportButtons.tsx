"use client";

import Button from "@/components/ui/Button";

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
      <Button
        variant="outline"
        onClick={onExportJsonl}
        disabled={loading !== null}
      >
        {loading === "jsonl" ? "Exporting…" : "Export JSONL"}
      </Button>

      <Button
        variant="outline"
        onClick={onExportCsv}
        disabled={loading !== null}
      >
        {loading === "csv" ? "Exporting…" : "Export CSV"}
      </Button>
    </div>
  );
}
