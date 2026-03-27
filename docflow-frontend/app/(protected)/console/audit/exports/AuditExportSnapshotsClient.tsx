"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { apiFetch } from "@/lib/apiFetch";
import { buildRangeQueryParams } from "@/lib/audit/auditRange";
import { useCursorPagination } from "@/lib/audit/useCursorPagination";
import { useDefaultAuditRange } from "@/lib/audit/useDefaultAuditRange";
import { adaptCursorPage } from "@/lib/audit/adaptCursorPage";
import { useAuditFilters } from "@/lib/audit/useAuditFilters";

import { AuditRangePanel } from "@/lib/audit/AuditRangePanel";
import { AuditCursorPagination } from "@/lib/audit/AuditCursorPagination";
import { AuditFiltersPanel } from "@/components/audit/filters/AuditFiltersPanel";
import { AuditTable } from "@/components/audit/AuditTable";

import TableToolbar from "@/components/ui/TableToolbar";

import { auditExportSnapshotsStream } from "@/lib/audit/streams/auditExportSnapshotsStream";

import type { AuditExportSnapshotDTO } from "@/types/api/AuditExportSnapshotDTO";
import { AuditExportVerificationModal } from "./AuditExportVerificationModal";
import EmptyState from "@/components/ui/EmptyState";

type CursorPage<T> = {
  items: T[];
  hasMore: boolean;
  nextCursorTimestamp?: string;
  nextCursorId?: string;
};

export function AuditExportSnapshotsClient() {
  const { filters, setFilter, syncToUrl } = useAuditFilters<
    Record<string, string>
  >(
    auditExportSnapshotsStream.key,
    {} as Record<string, string>,
    auditExportSnapshotsStream.filterDefinitions,
  );

  const { from, to, size, setFrom, setTo, setSize } = useDefaultAuditRange();

  const {
    rows,
    nextCursorTimestamp,
    nextCursorId,
    hasNext,
    reset,
    applyFirstPage,
    appendPage,
  } = useCursorPagination<AuditExportSnapshotDTO>();

  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [queried, setQueried] = useState(false);

  const [snapshot, setSnapshot] = useState<AuditExportSnapshotDTO | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const requestIdRef = useRef(0);
  const inFlightRef = useRef(false);

  const buildParams = useCallback(
    (cursorTs?: string, cursorId?: string) => {
      const qs = buildRangeQueryParams({ from, to });

      Object.entries(filters).forEach(([key, value]) => {
        if (value && value.trim()) {
          qs.set(key, value.trim());
        }
      });

      if (cursorTs) qs.set("cursorTimestamp", cursorTs);
      if (cursorId) qs.set("cursorId", cursorId);

      qs.set("size", String(size));

      return qs;
    },
    [filters, from, to, size],
  );

  const runQuery = useCallback(async () => {
    const requestId = ++requestIdRef.current;

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    setLoading(true);
    setError(null);

    try {
      const data = await apiFetch<CursorPage<AuditExportSnapshotDTO>>(
        `${auditExportSnapshotsStream.endpoint}?${buildParams().toString()}`,
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
  }, [applyFirstPage, buildParams, reset]);

  const triggerQuery = useCallback(() => {
    if (inFlightRef.current) return;

    syncToUrl();
    inFlightRef.current = true;

    runQuery().finally(() => {
      inFlightRef.current = false;
    });
  }, [runQuery, syncToUrl]);

  useEffect(() => {
    function handler(e: Event) {
      const custom = e as CustomEvent<AuditExportSnapshotDTO>;
      setSnapshot(custom.detail);
    }

    window.addEventListener("audit:snapshot:verify", handler);

    return () => {
      window.removeEventListener("audit:snapshot:verify", handler);
    };
  }, []);

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  async function handleLoadMore() {
    if (!hasNext || !nextCursorTimestamp || !nextCursorId) return;
    if (inFlightRef.current) return;

    inFlightRef.current = true;
    setLoadingMore(true);

    try {
      const data = await apiFetch<CursorPage<AuditExportSnapshotDTO>>(
        `${auditExportSnapshotsStream.endpoint}?${buildParams(
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

  const renderFilters = useMemo(() => {
    if (!auditExportSnapshotsStream.filterDefinitions?.length) return null;

    return (
      <AuditFiltersPanel
        filters={filters}
        setFilter={setFilter}
        triggerQuery={triggerQuery}
        definitions={auditExportSnapshotsStream.filterDefinitions}
      />
    );
  }, [filters, setFilter, triggerQuery]);

  return (
    <div>
      <div className="flex flex-col gap-6">
        {/* Query */}
        <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
          <div className="flex flex-col gap-4">
            <AuditRangePanel
              from={from}
              to={to}
              size={size}
              onFromChange={setFrom}
              onToChange={setTo}
              onSizeChange={setSize}
              onQuery={triggerQuery}
              loading={loading}
            />

            <TableToolbar filters={renderFilters} />
          </div>
        </section>

        {error && <div className="text-sm text-(--color-error)">{error}</div>}

        <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
          {!queried && !loading && (
            <EmptyState
              title="No data loaded"
              description="Select a date range and run the query to view audit export snapshots."
            />
          )}

          {(rows.length > 0 || loading || queried) && (
            <>
              <div className={loading ? "opacity-60" : ""}>
                <AuditTable
                  rows={rows}
                  columns={auditExportSnapshotsStream.columns}
                  rowKey={(r) => r.id}
                  setFilter={(k, v) => setFilter(k as keyof typeof filters, v)}
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
            <EmptyState
              title="No results"
              description="No audit export snapshots found for the selected range."
            />
          )}
        </section>
      </div>

      <AuditExportVerificationModal
        open={!!snapshot}
        snapshot={snapshot}
        onClose={() => setSnapshot(null)}
      />
    </div>
  );
}
