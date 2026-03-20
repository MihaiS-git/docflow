"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

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

import TableToolbar from "@/components/ui/TableToolbar";

type CursorPage<T> = {
  items: T[];
  hasMore: boolean;
  nextCursorTimestamp?: string;
  nextCursorId?: string;
};

type FilterDefinition = {
  key: string;
  validate?: (value: string) => boolean;
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
  filterDefinitions?: FilterDefinition[];
  onRowAction?: (type: string, payload: unknown) => void;
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
  filterDefinitions,
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

  const inFlightRef = useRef(false);

  // compressed race-safe logic
  const abortRef = useRef<AbortController | null>(null);
  const requestIdRef = useRef(0);

  const validators = useMemo(() => {
    const next: Record<string, ((value: string) => boolean) | undefined> = {};

    (filterDefinitions ?? []).forEach((definition) => {
      if (definition.validate) {
        next[definition.key] = definition.validate;
      }
    });

    return next;
  }, [filterDefinitions]);

  const buildParams = useCallback(
    (cursorTs?: string, cursorId?: string) => {
      const qs = buildRangeQueryParams({ from, to });

      Object.entries(filters).forEach(([key, value]) => {
        if (typeof value === "string" && value.trim()) {
          const v = value.trim();
          const validator = validators[key];
          if (validator && !validator(v)) return;
          qs.set(key, v);
        }
      });

      if (cursorTs) qs.set("cursorTimestamp", cursorTs);
      if (cursorId) qs.set("cursorId", cursorId);

      qs.set("size", String(size));

      return qs;
    },
    [filters, from, size, to, validators],
  );

  const runQuery = useCallback(async () => {
    const requestId = ++requestIdRef.current;

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setLoading(true);
    setError(null);
    setVerifyResult(null);

    try {
      const data = await apiFetch<CursorPage<T>>(
        `${endpoint}?${buildParams().toString()}`,
        { signal: controller.signal },
      );

      if (requestId !== requestIdRef.current) return;

      reset();
      applyFirstPage(adaptCursorPage(data));
      setQueried(true);
    } catch (e) {
      if ((e as DOMException)?.name === "AbortError") return;
      if (requestId !== requestIdRef.current) return;

      setError(e instanceof Error ? e.message : "Query failed");
    } finally {
      if (requestId === requestIdRef.current) {
        setLoading(false);
      }
    }
  }, [applyFirstPage, buildParams, endpoint, reset]);

  const handleQuery = useCallback(() => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;

    runQuery().finally(() => {
      inFlightRef.current = false;
    });
  }, [runQuery]);

  useEffect(() => {
    if (!queryNonce) return;
    if (inFlightRef.current) return;

    inFlightRef.current = true;

    runQuery().finally(() => {
      inFlightRef.current = false;
    });

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryNonce]);

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  async function handleLoadMore() {
    if (!hasNext || !nextCursorTimestamp || !nextCursorId) return;
    if (inFlightRef.current) return;

    inFlightRef.current = true;
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
      inFlightRef.current = false;
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

    Object.entries(filters).forEach(([key, value]) => {
      if (typeof value === "string" && value.trim()) {
        const v = value.trim();
        const validator = validators[key];
        if (validator && !validator(v)) return;
        qs.set(key, v);
      }
    });

    return qs;
  }, [filters, from, to, validators]);

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
    <div className="flex flex-col gap-6">
      <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
        <div className="flex flex-col gap-4">
          <div>
            <h2 className="text-sm font-semibold text-(--color-text-primary)">
              Query
            </h2>
            <p className="mt-1 text-xs text-(--color-text-muted)">
              Select range and filters before running the audit query.
            </p>
          </div>

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

          <TableToolbar filters={renderFilters()} />
        </div>
      </section>

      <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
        <div className="mb-4">
          <h2 className="text-sm font-semibold text-(--color-text-primary)">
            Actions
          </h2>
          <p className="mt-1 text-xs text-(--color-text-muted)">
            Verify integrity for the selected range or export the current scope.
          </p>
        </div>

        <TableToolbar
          filters={
            <div className="min-w-[320px] max-w-xl">
              <AuditVerifyPanel
                verifying={verifying}
                onVerify={handleVerify}
                result={verifyResult}
                from={from}
                to={to}
              />
            </div>
          }
          actions={
            <AuditExportButtons
              onExportJsonl={() => handleExport("jsonl")}
              onExportCsv={() => handleExport("csv")}
              loading={downloading}
            />
          }
        />
      </section>

      {error && <div className="text-sm text-(--color-error)">{error}</div>}

      <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-(--color-text-primary)">
            {title}
          </h2>

          {loading && (
            <span className="text-xs text-(--color-text-muted)">Updating…</span>
          )}
        </div>

        {(rows.length > 0 || loading || queried) && (
          <>
            <div className={loading ? "opacity-60 transition-opacity" : ""}>
              <AuditTable
                rows={rows}
                columns={columns}
                rowKey={rowKey}
                setFilter={setFilter}
                triggerQuery={triggerQuery}
              />
            </div>

            {rows.length > 0 && (
              <div className="mt-4">
                <AuditCursorPagination
                  hasNext={hasNext}
                  loading={loadingMore}
                  onLoadMore={handleLoadMore}
                />
              </div>
            )}
          </>
        )}

        {queried && !loading && rows.length === 0 && !error && (
          <div className="text-sm text-(--color-text-muted)">
            No results found.
          </div>
        )}
      </section>
    </div>
  );
}
