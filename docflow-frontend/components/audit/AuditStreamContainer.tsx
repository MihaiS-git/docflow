"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { downloadAuditFile } from "@/lib/audit/auditDownload";
import { buildRangeQueryParams } from "@/lib/audit/auditRange";
import { useCursorPagination } from "@/lib/audit/useCursorPagination";
import { useDefaultAuditRange } from "@/lib/audit/useDefaultAuditRange";
import { adaptCursorPage } from "@/lib/audit/adaptCursorPage";
import { createVerifyHandler } from "@/lib/audit/createVerifyHandler";
import { AuditRangePanel } from "@/lib/audit/AuditRangePanel";
import { AuditExportButtons } from "@/lib/audit/AuditExportButtons";
import { AuditVerifyPanel } from "@/lib/audit/AuditVerifyPanel";
import { AuditCursorPagination } from "@/lib/audit/AuditCursorPagination";
import { AuditTable } from "./AuditTable";
import type { AuditVerificationResultDTO } from "@/lib/api/AuditVerificationResultDTO";
import type { AuditColumn } from "./AuditColumn";

type CursorPage<T> = {
  items: T[];
  hasMore: boolean;
  nextCursorTimestamp?: string;
  nextCursorId?: string;
};

type Props<T> = {
  title: string;
  endpoint: string;
  filenameBase: string;

  filters: Record<string, string>;
  renderFilters: () => React.ReactNode;

  columns: AuditColumn<T>[];
  rowKey: (row: T) => string;

  setFilter?: (key: string, value: string) => void;
  queryNonce?: number;
  triggerQuery?: () => void;
};

export function AuditStreamContainer<T>({
  title,
  endpoint,
  filenameBase,
  filters,
  renderFilters,
  columns,
  rowKey,
  setFilter,
  queryNonce,
  triggerQuery,
}: Props<T>) {
  const { from, to, size, setFrom, setTo, setSize } = useDefaultAuditRange();

  const {
    rows,
    nextCursorTimestamp,
    nextCursorId,
    hasNext,
    reset,
    applyFirstPage,
    appendPage,
  } = useCursorPagination<T>();

  const [verifyResult, setVerifyResult] =
    useState<AuditVerificationResultDTO | null>(null);

  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [downloading, setDownloading] = useState<"jsonl" | "csv" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [queried, setQueried] = useState(false);

  const buildParams = useCallback(
    (cursorTs?: string, cursorId?: string) => {
      const qs = buildRangeQueryParams({ from, to });

      Object.entries(filters).forEach(([k, v]) => {
        if (typeof v === "string" && v.trim()) {
          qs.set(k, v.trim());
        }
      });

      if (cursorTs) qs.set("cursorTimestamp", cursorTs);
      if (cursorId) qs.set("cursorId", cursorId);

      qs.set("size", String(size));

      return qs;
    },
    [filters, from, size, to],
  );

  const handleQuery = useCallback(async () => {
    setLoading(true);
    setError(null);
    setVerifyResult(null);

    try {
      reset();

      const data = await apiFetch<CursorPage<T>>(
        `${endpoint}?${buildParams().toString()}`,
      );

      applyFirstPage(adaptCursorPage(data));
      setQueried(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Query failed");
    } finally {
      setLoading(false);
    }
  }, [applyFirstPage, buildParams, endpoint, reset]);

  useEffect(() => {
    if (!queryNonce) return;

    void handleQuery();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryNonce]);

  async function handleLoadMore() {
    if (!hasNext || !nextCursorTimestamp || !nextCursorId) return;

    setLoadingMore(true);

    try {
      const data = await apiFetch<CursorPage<T>>(
        `${endpoint}?${buildParams(
          nextCursorTimestamp,
          nextCursorId,
        ).toString()}`,
      );

      appendPage(adaptCursorPage(data));
    } finally {
      setLoadingMore(false);
    }
  }

  const handleVerify = createVerifyHandler(
    endpoint,
    from,
    to,
    setVerifyResult,
    setVerifying,
  );

  const buildExportParams = useCallback(() => {
    const qs = buildRangeQueryParams({ from, to });

    Object.entries(filters).forEach(([k, v]) => {
      if (typeof v === "string" && v.trim()) {
        qs.set(k, v.trim());
      }
    });

    return qs;
  }, [filters, from, to]);

  const handleExport = useCallback(
    async (format: "jsonl" | "csv") => {
      setDownloading(format);

      try {
        const qs = buildExportParams();

        const path =
          format === "jsonl" ? `${endpoint}/export` : `${endpoint}/export/csv`;

        const filename =
          format === "jsonl" ? `${filenameBase}.jsonl` : `${filenameBase}.csv`;

        await downloadAuditFile({
          path: `${path}?${qs.toString()}`,
          filename,
        });
      } finally {
        setDownloading(null);
      }
    },
    [buildExportParams, endpoint, filenameBase],
  );

  return (
    <div className="p-4 space-y-6">
      <h1 className="text-lg font-semibold">{title}</h1>

      <AuditRangePanel
        from={from}
        to={to}
        size={size}
        onFromChange={setFrom}
        onToChange={setTo}
        onSizeChange={setSize}
        onQuery={handleQuery}
        loading={loading}
      />

      {renderFilters && renderFilters()}

      <div className="grid gap-4 md:grid-cols-2">
        <AuditVerifyPanel
          verifying={verifying}
          onVerify={handleVerify}
          result={verifyResult}
          from={from}
          to={to}
        />

        <AuditExportButtons
          onExportJsonl={() => handleExport("jsonl")}
          onExportCsv={() => handleExport("csv")}
          loading={downloading}
        />
      </div>

      {error && <div className="text-sm text-red-600">{error}</div>}

      {rows.length > 0 && (
        <>
          <AuditTable
            rows={rows}
            columns={columns}
            rowKey={rowKey}
            setFilter={setFilter}
            triggerQuery={triggerQuery}
          />

          <AuditCursorPagination
            hasNext={hasNext}
            loading={loadingMore}
            onLoadMore={handleLoadMore}
          />
        </>
      )}

      {queried && rows.length === 0 && !error && (
        <div className="text-sm text-gray-600">No results found.</div>
      )}
    </div>
  );
}
