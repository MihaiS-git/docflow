"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { AuditCursorPagination } from "@/lib/audit/AuditCursorPagination";
import {
  toDateTimeLocalString,
  formatAuditTimestamp,
} from "@/lib/date/dateTimeLocal";
import { toIsoOrThrow } from "@/lib/audit/auditRange";

import type { AuditExportSnapshotDTO } from "@/types/api/AuditExportSnapshotDTO";
import type { AuditExportSnapshotCursorPageDTO } from "@/types/api/AuditExportSnapshotCursorPageDTO";
import { AuditExportVerificationModal } from "./AuditExportVerificationModal";

type Filters = {
  stream: string;
  tenantId: string;
  snapshotId: string;
  createdFrom: string;
  createdTo: string;
};

function buildSnapshotQuery(
  filters: Filters,
  size: number,
  cursor?: { createdAt: string; id: string },
) {
  const qs = new URLSearchParams();
  qs.set("size", String(size));

  if (filters.stream) qs.set("stream", filters.stream);
  if (filters.tenantId) qs.set("tenantId", filters.tenantId);
  if (filters.snapshotId) qs.set("snapshotId", filters.snapshotId);

  if (filters.createdFrom)
    qs.set("createdFrom", toIsoOrThrow(filters.createdFrom, "createdFrom"));

  if (filters.createdTo)
    qs.set("createdTo", toIsoOrThrow(filters.createdTo, "createdTo"));

  if (cursor) {
    qs.set("cursorCreatedAt", cursor.createdAt);
    qs.set("cursorId", cursor.id);
  }

  return qs;
}

const DEFAULT_PAGE_SIZE = 20;

export function AuditExportSnapshotsClient() {
  const [filters, setFilters] = useState<Filters>(() => {
    const now = new Date();
    const to = toDateTimeLocalString(now);
    const from = toDateTimeLocalString(
      new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000),
    );

    return {
      stream: "",
      tenantId: "",
      snapshotId: "",
      createdFrom: from,
      createdTo: to,
    };
  });

  const [items, setItems] = useState<AuditExportSnapshotDTO[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<{
    createdAt: string;
    id: string;
  } | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [verifyOpen, setVerifyOpen] = useState(false);
  const [verifySnapshot, setVerifySnapshot] =
    useState<AuditExportSnapshotDTO | null>(null);

  const streamOptions = useMemo(() => {
    const s = new Set<string>();
    for (const it of items) {
      if (it.stream) s.add(it.stream);
    }
    return ["", ...Array.from(s).sort((a, b) => a.localeCompare(b))];
  }, [items]);

  const fetchFirstPage = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const qs = buildSnapshotQuery(filters, DEFAULT_PAGE_SIZE);

      const page = await apiFetch<AuditExportSnapshotCursorPageDTO>(
        `/api/audit/exports?${qs.toString()}`,
      );

      setItems(page.items);
      setHasMore(page.hasMore);

      if (page.hasMore && page.nextCursorCreatedAt && page.nextCursorId) {
        setNextCursor({
          createdAt: page.nextCursorCreatedAt,
          id: page.nextCursorId,
        });
      } else {
        setNextCursor(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load snapshots");
      setItems([]);
      setHasMore(false);
      setNextCursor(null);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  const fetchMore = useCallback(async () => {
    if (!hasMore || !nextCursor || loading) return;

    setLoading(true);
    try {
      const qs = buildSnapshotQuery(filters, DEFAULT_PAGE_SIZE, nextCursor);

      const page = await apiFetch<AuditExportSnapshotCursorPageDTO>(
        `/api/audit/exports?${qs.toString()}`,
      );

      setItems((prev) => [...prev, ...page.items]);
      setHasMore(page.hasMore);

      if (page.hasMore && page.nextCursorCreatedAt && page.nextCursorId) {
        setNextCursor({
          createdAt: page.nextCursorCreatedAt,
          id: page.nextCursorId,
        });
      } else {
        setNextCursor(null);
      }
    } finally {
      setLoading(false);
    }
  }, [filters, hasMore, nextCursor, loading]);

  useEffect(() => {
    void fetchFirstPage();
  }, [fetchFirstPage]);

  function onChange<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }

  function onOpenVerify(s: AuditExportSnapshotDTO) {
    setVerifySnapshot(s);
    setVerifyOpen(true);
  }

  function onCloseVerify() {
    setVerifyOpen(false);
    setVerifySnapshot(null);
  }

  return (
    <div className="space-y-4">
      <header className="space-y-1">
        <h1 className="text-xl font-semibold">Audit Export Snapshots</h1>
        <p className="text-sm text-neutral-600">
          Registry and offline verification for sealed JSONL exports.
        </p>
      </header>

      <section className="rounded border p-3 space-y-3">
        <div className="grid gap-3 md:grid-cols-5">
          <input
            className="border rounded px-2 py-1"
            placeholder="Snapshot ID"
            value={filters.snapshotId}
            onChange={(e) => onChange("snapshotId", e.target.value)}
          />

          <select
            className="border rounded px-2 py-1"
            value={filters.stream}
            onChange={(e) => onChange("stream", e.target.value)}
          >
            {streamOptions.map((v) => (
              <option key={v || "__ALL__"} value={v}>
                {v ? v : "All streams"}
              </option>
            ))}
          </select>

          <input
            className="border rounded px-2 py-1"
            placeholder="Tenant ID"
            value={filters.tenantId}
            onChange={(e) => onChange("tenantId", e.target.value)}
          />

          <input
            type="datetime-local"
            className="border rounded px-2 py-1"
            value={filters.createdFrom}
            onChange={(e) => onChange("createdFrom", e.target.value)}
          />

          <input
            type="datetime-local"
            className="border rounded px-2 py-1"
            value={filters.createdTo}
            onChange={(e) => onChange("createdTo", e.target.value)}
          />
        </div>

        <button
          onClick={fetchFirstPage}
          className="rounded bg-black px-3 py-1.5 text-sm text-white"
        >
          Apply
        </button>
      </section>

      {error ? (
        <p role="alert" className="text-sm text-red-700">
          {error}
        </p>
      ) : null}

      <section className="rounded border">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="border-b bg-neutral-50">
              <tr>
                <th className="px-3 py-2 text-black">Snapshot ID</th>
                <th className="px-3 py-2 text-black">Created</th>
                <th className="px-3 py-2 text-black">Stream</th>
                <th className="px-3 py-2 text-black">Tenant</th>
                <th className="px-3 py-2 text-black">Rows</th>
                <th className="px-3 py-2 text-black">KeyId</th>
                <th className="px-3 py-2 text-black">Digest</th>
                <th className="px-3 py-2 text-black">Actions</th>
              </tr>
            </thead>
            <tbody>
              {items.map((s) => (
                <tr key={s.id} className="border-b">
                  <td className="px-3 py-2 font-mono break-all">{s.id}</td>
                  <td className="px-3 py-2">
                    {formatAuditTimestamp(s.createdAt)}
                  </td>
                  <td className="px-3 py-2">{s.stream}</td>
                  <td className="px-3 py-2">{s.tenantId ?? "—"}</td>
                  <td className="px-3 py-2">{s.rowCount}</td>
                  <td className="px-3 py-2 font-mono">{s.keyId ?? "—"}</td>
                  <td className="px-3 py-2 font-mono">
                    {s.sha256DigestHex?.slice(0, 12)}
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-2">
                      <button
                        className="rounded border px-2 py-1"
                        onClick={() => onOpenVerify(s)}
                      >
                        Verify
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {items.length === 0 ? (
                <tr>
                  <td
                    className="px-3 py-4 text-sm text-neutral-600"
                    colSpan={8}
                  >
                    No snapshots.
                  </td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>

        <div className="p-3">
          <AuditCursorPagination
            hasNext={hasMore}
            loading={loading}
            onLoadMore={fetchMore}
          />
        </div>
      </section>

      <AuditExportVerificationModal
        open={verifyOpen}
        snapshot={verifySnapshot}
        onClose={onCloseVerify}
      />
    </div>
  );
}
