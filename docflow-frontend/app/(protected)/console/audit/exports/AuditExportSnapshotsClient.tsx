"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { downloadAuditFile } from "@/lib/audit/auditDownload";
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

type CurrentAuditKeyJson = {
  keyId: string;
  algorithm: string;
  publicKeyPem: string;
};

const STREAM_EXPORT_ENDPOINT_BY_STREAM: Record<string, string> = {
  ADMIN_ACTIONS: "/api/audit/admin-actions",
  AUTHENTICATION: "/api/audit/authentication",
  CREDENTIAL_LIFECYCLE: "/api/audit/credential-lifecycle",
  IDENTITY_PROJECTION: "/api/audit/identity-projection",
  LIFECYCLE_DENIED: "/api/audit/lifecycle-denied",
  ONBOARDING: "/api/audit/onboarding",
  RBAC_DENIED: "/api/audit/rbac-denied",
  SENSITIVE_ACCESS: "/api/audit/sensitive-access",
  UNAUTHENTICATED_ACCESS: "/api/audit/unauthenticated-access",
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

function buildRegeneratedExportPath(s: AuditExportSnapshotDTO): string {
  const endpointBase = STREAM_EXPORT_ENDPOINT_BY_STREAM[s.stream];
  if (!endpointBase) {
    throw new Error(
      `Unknown stream "${s.stream}" (no export endpoint mapping).`,
    );
  }

  const qs = new URLSearchParams();
  qs.set("from", s.fromTs);
  qs.set("to", s.toTs);
  if (s.tenantId) qs.set("tenantId", s.tenantId);

  return `${endpointBase}/export?${qs.toString()}`;
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

  // Global key viewer (single current key)
  const [keyPanelOpen, setKeyPanelOpen] = useState(false);
  const [currentKey, setCurrentKey] = useState<CurrentAuditKeyJson | null>(
    null,
  );
  const [keyLoading, setKeyLoading] = useState(false);
  const [keyError, setKeyError] = useState<string | null>(null);

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

  function onDownloadJsonl(snapshot: AuditExportSnapshotDTO) {
    try {
      const path = buildRegeneratedExportPath(snapshot);
      downloadAuditFile({
        path,
        filename: `audit-export-${snapshot.id}.jsonl`,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Download failed");
    }
  }

  function onOpenVerify(s: AuditExportSnapshotDTO) {
    setVerifySnapshot(s);
    setVerifyOpen(true);
  }

  function onCloseVerify() {
    setVerifyOpen(false);
    setVerifySnapshot(null);
  }

  async function fetchCurrentKey() {
    setKeyLoading(true);
    setKeyError(null);
    try {
      const json = await apiFetch<CurrentAuditKeyJson>(
        "/api/security/audit-keys/current.json",
      );
      setCurrentKey(json);
    } catch (e) {
      setKeyError(
        e instanceof Error ? e.message : "Failed to load current key",
      );
      setCurrentKey(null);
    } finally {
      setKeyLoading(false);
    }
  }

  function onToggleKeyPanel() {
    setKeyPanelOpen((v) => {
      const next = !v;
      if (next && !currentKey && !keyLoading) {
        void fetchCurrentKey();
      }
      return next;
    });
  }

  function onDownloadCurrentPublicKeyPem() {
    downloadAuditFile({
      path: "/api/security/audit-keys/current",
      filename: `audit-export-public-key-current.pem`,
    });
  }

  return (
    <div className="space-y-4">
      <header className="space-y-1">
        <h1 className="text-xl font-semibold">Audit Export Snapshots</h1>
        <p className="text-sm text-neutral-600">
          Registry and offline verification for sealed JSONL exports.
        </p>
      </header>

      {/* Global key viewer (single current key) */}
      <section className="rounded border p-3 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="text-sm font-semibold">
              Audit export signing key
            </div>
            <div className="text-xs text-neutral-600">
              Single global current key (not per snapshot).
            </div>
          </div>

          <button
            type="button"
            onClick={onToggleKeyPanel}
            className="rounded border px-2 py-1 text-sm"
          >
            {keyPanelOpen ? "Hide" : "Show"}
          </button>
        </div>

        {keyPanelOpen ? (
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <button
                type="button"
                onClick={() => void fetchCurrentKey()}
                className="rounded border px-2 py-1 text-sm disabled:opacity-50"
                disabled={keyLoading}
              >
                {keyLoading ? "Loading…" : "Refresh"}
              </button>

              <button
                type="button"
                onClick={onDownloadCurrentPublicKeyPem}
                className="rounded border px-2 py-1 text-sm"
              >
                Download PEM
              </button>

              {keyError ? (
                <span role="alert" className="text-sm text-red-700">
                  {keyError}
                </span>
              ) : null}
            </div>

            {currentKey ? (
              <div className="rounded border p-2 text-sm space-y-1">
                <div>
                  <span className="text-neutral-600">keyId:</span>{" "}
                  <span className="font-mono">{currentKey.keyId}</span>
                </div>
                <div>
                  <span className="text-neutral-600">algorithm:</span>{" "}
                  <span className="font-mono">{currentKey.algorithm}</span>
                </div>
                <div className="text-xs text-neutral-600">publicKeyPem:</div>
                <pre className="overflow-x-auto whitespace-pre-wrap wrap-break-word rounded bg-neutral-50 p-2 text-xs">
                  {currentKey.publicKeyPem}
                </pre>
              </div>
            ) : null}
          </div>
        ) : null}
      </section>

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
                <th className="px-3 py-2">Snapshot ID</th>
                <th className="px-3 py-2">Created</th>
                <th className="px-3 py-2">Stream</th>
                <th className="px-3 py-2">Tenant</th>
                <th className="px-3 py-2">Rows</th>
                <th className="px-3 py-2">KeyId</th>
                <th className="px-3 py-2">Digest</th>
                <th className="px-3 py-2">Actions</th>
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
                        onClick={() => onDownloadJsonl(s)}
                      >
                        Download JSONL
                      </button>
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
