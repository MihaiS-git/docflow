"use client";

import { useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { buildRangeQueryParams } from "@/lib/audit/auditRange";
import { useCursorPagination } from "@/lib/audit/useCursorPagination";
import { useDefaultAuditRange } from "@/lib/audit/useDefaultAuditRange";
import { adaptCursorPage } from "@/lib/audit/adaptCursorPage";
import { createVerifyHandler } from "@/lib/audit/createVerifyHandler";
import { createJsonlExportHandler } from "@/lib/audit/createJsonlExportHandler";
import { AuditRangePanel } from "@/lib/audit/AuditRangePanel";
import { AuditVerifyPanel } from "@/lib/audit/AuditVerifyPanel";
import { AuditCursorPagination } from "@/lib/audit/AuditCursorPagination";
import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { RbacDeniedAuditCursorPageDTO } from "@/types/api/RbacDeniedAuditCursorPageDTO";
import { AuditVerificationResultDTO } from "@/lib/api/AuditVerificationResultDTO";
import { RbacDeniedAuditRow } from "@/types/api/RbacDeniedAuditRow";

export default function RbacDeniedAuditClient() {
  const { from, to, size, setFrom, setTo, setSize } =
    useDefaultAuditRange();

  const [correlationId, setCorrelationId] = useState("");
  const [subjectId, setSubjectId] = useState("");

  const {
    rows,
    nextCursorTimestamp,
    nextCursorId,
    hasNext,
    reset,
    applyFirstPage,
    appendPage,
  } = useCursorPagination<RbacDeniedAuditRow>();

  const [verifyResult, setVerifyResult] =
    useState<AuditVerificationResultDTO | null>(null);

  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [queried, setQueried] = useState(false);

  function buildParams(cursorTs?: string, cursorId?: string) {
    const qs = buildRangeQueryParams({ from, to });

    if (correlationId.trim()) {
      qs.set("correlationId", correlationId.trim());
    }

    if (subjectId.trim()) {
      qs.set("subjectId", subjectId.trim());
    }

    if (cursorTs) qs.set("cursorTimestamp", cursorTs);
    if (cursorId) qs.set("cursorId", cursorId);

    qs.set("size", String(size));
    return qs;
  }

  async function handleQuery() {
    setLoading(true);
    setError(null);
    setVerifyResult(null);

    try {
      reset();

      const data =
        await apiFetch<RbacDeniedAuditCursorPageDTO>(
          `/api/audit/rbac-denied?${buildParams().toString()}`
        );

      applyFirstPage(adaptCursorPage(data));
      setQueried(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Query failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleLoadMore() {
    if (!hasNext || !nextCursorTimestamp || !nextCursorId) return;

    setLoadingMore(true);

    try {
      const data =
        await apiFetch<RbacDeniedAuditCursorPageDTO>(
          `/api/audit/rbac-denied?${buildParams(
            nextCursorTimestamp,
            nextCursorId
          ).toString()}`
        );

      appendPage(adaptCursorPage(data));
    } finally {
      setLoadingMore(false);
    }
  }

  const handleVerify = createVerifyHandler(
    "/api/audit/rbac-denied",
    from,
    to,
    setVerifyResult,
    setVerifying
  );

  const handleExportJsonl = createJsonlExportHandler(
    "/api/audit/rbac-denied",
    "rbac-denied-export.jsonl",
    from,
    to,
    (qs) => {
      if (correlationId.trim()) {
        qs.set("correlationId", correlationId.trim());
      }
      if (subjectId.trim()) {
        qs.set("subjectId", subjectId.trim());
      }
    },
    setDownloading,
    setError
  );

  return (
    <div className="p-4 space-y-6">
      <h1 className="text-lg font-semibold">RBAC Denied Audit</h1>

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

      <div className="border rounded p-3 grid gap-3 md:grid-cols-2">
        <input
          className="border rounded px-2 py-1"
          placeholder="Correlation ID"
          value={correlationId}
          onChange={(e) => setCorrelationId(e.target.value)}
        />
        <input
          className="border rounded px-2 py-1"
          placeholder="Subject ID"
          value={subjectId}
          onChange={(e) => setSubjectId(e.target.value)}
        />
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <AuditVerifyPanel
          verifying={verifying}
          onVerify={handleVerify}
          result={verifyResult}
          from={from}
          to={to}
        />

        <div>
          <button
            type="button"
            onClick={handleExportJsonl}
            disabled={downloading}
            className="px-3 py-1 rounded border disabled:opacity-50"
          >
            {downloading ? "Exporting…" : "Export JSONL"}
          </button>
        </div>
      </div>

      {error && (
        <div className="text-sm text-red-600">{error}</div>
      )}

      {rows.length > 0 && (
        <>
          <div className="overflow-auto border rounded">
            <table className="min-w-full text-xs">
              <thead>
                <tr>
                  <th className="p-2 border-b">timestamp</th>
                  <th className="p-2 border-b">subjectId</th>
                  <th className="p-2 border-b">method</th>
                  <th className="p-2 border-b">path</th>
                  <th className="p-2 border-b">ip</th>
                  <th className="p-2 border-b">result</th>
                  <th className="p-2 border-b">fingerprint</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r, idx) => (
                  <tr key={idx}>
                    <td className="p-2 font-mono">
                      {formatAuditTimestamp(r.timestamp)}
                    </td>
                    <td className="p-2">{r.subjectId}</td>
                    <td className="p-2">{r.httpMethod}</td>
                    <td className="p-2">{r.path}</td>
                    <td className="p-2">{r.ip}</td>
                    <td className="p-2">
                      {String(r.result)}
                    </td>
                    <td className="p-2 font-mono">
                      {r.eventFingerprint}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <AuditCursorPagination
            hasNext={hasNext}
            loading={loadingMore}
            onLoadMore={handleLoadMore}
          />
        </>
      )}

      {queried && rows.length === 0 && !error && (
        <div className="text-sm text-gray-600">
          No results found.
        </div>
      )}
    </div>
  );
}
