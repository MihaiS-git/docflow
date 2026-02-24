"use client";

import { useState } from "react";
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
import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import { CredentialLifecycleAuditCursorPageDTO } from "@/types/api/CredentialLifecycleAuditCursorPageDTO";
import { CredentialLifecycleAuditRow } from "@/types/api/CredentialLifecycleAuditRow";
import { AuditVerificationResultDTO } from "@/lib/api/AuditVerificationResultDTO";
import { ResultBadge } from "@/components/audit/ResultBadge";
import { normalizeAuditResult } from "@/lib/audit/normalizeAuditResult";

export default function CredentialLifecycleAuditClient() {
  const { from, to, size, setFrom, setTo, setSize } = useDefaultAuditRange();

  const [correlationId, setCorrelationId] = useState("");
  const [subjectExternalId, setSubjectExternalId] = useState("");
  const [result, setResult] = useState("");

  const {
    rows,
    nextCursorTimestamp,
    nextCursorId,
    hasNext,
    reset,
    applyFirstPage,
    appendPage,
  } = useCursorPagination<CredentialLifecycleAuditRow>();

  const [verifyResult, setVerifyResult] =
    useState<AuditVerificationResultDTO | null>(null);

  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [downloading, setDownloading] = useState<"jsonl" | "csv" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [queried, setQueried] = useState(false);

  function buildParams(cursorTs?: string, cursorId?: string) {
    const qs = buildRangeQueryParams({ from, to });

    if (correlationId.trim()) qs.set("correlationId", correlationId.trim());
    if (subjectExternalId.trim())
      qs.set("subjectExternalId", subjectExternalId.trim());
    if (result.trim()) qs.set("result", result.trim());

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

      const data = await apiFetch<CredentialLifecycleAuditCursorPageDTO>(
        `/api/audit/credential-lifecycle?${buildParams().toString()}`,
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
      const data = await apiFetch<CredentialLifecycleAuditCursorPageDTO>(
        `/api/audit/credential-lifecycle?${buildParams(
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
    "/api/audit/credential-lifecycle",
    from,
    to,
    setVerifyResult,
    setVerifying,
  );

  async function handleExportJsonl() {
    setDownloading("jsonl");
    try {
      const qs = buildRangeQueryParams({ from, to });

      await downloadAuditFile({
        path: `/api/audit/credential-lifecycle/export?${qs.toString()}`,
        filename: "credential-lifecycle-audit-export.jsonl",
      });
    } finally {
      setDownloading(null);
    }
  }

  async function handleExportCsv() {
    setDownloading("csv");
    try {
      const qs = buildRangeQueryParams({ from, to });

      await downloadAuditFile({
        path: `/api/audit/credential-lifecycle/export/csv?${qs.toString()}`,
        filename: "credential-lifecycle-audit-export.csv",
      });
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="p-4 space-y-6">
      <h1 className="text-lg font-semibold">Credential Lifecycle Audit</h1>

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

      <div className="border rounded p-3 grid gap-3 md:grid-cols-3">
        <input
          className="border rounded px-2 py-1"
          placeholder="Correlation ID"
          value={correlationId}
          onChange={(e) => setCorrelationId(e.target.value)}
        />
        <input
          className="border rounded px-2 py-1"
          placeholder="Subject External ID"
          value={subjectExternalId}
          onChange={(e) => setSubjectExternalId(e.target.value)}
        />
        <input
          className="border rounded px-2 py-1"
          placeholder="Result"
          value={result}
          onChange={(e) => setResult(e.target.value)}
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

        <AuditExportButtons
          onExportJsonl={handleExportJsonl}
          onExportCsv={handleExportCsv}
          loading={downloading}
        />
      </div>

      {error && <div className="text-sm text-red-600">{error}</div>}

      {rows.length > 0 && (
        <>
          <div className="overflow-auto border rounded">
            <table className="min-w-full text-xs">
              <thead>
                <tr>
                  <th className="p-2 border-b">timestamp</th>
                  <th className="p-2 border-b">id</th>
                  <th className="p-2 border-b">subjectExternalId</th>
                  <th className="p-2 border-b">eventType</th>
                  <th className="p-2 border-b">result</th>
                  <th className="p-2 border-b">ip</th>
                  <th className="p-2 border-b">eventFingerprint</th>
                  <th className="p-2 border-b">correlationId</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td className="p-2 font-mono">
                      {formatAuditTimestamp(r.timestamp)}
                    </td>
                    <td className="p-2">{r.id}</td>
                    <td className="p-2">{r.subjectExternalId ?? ""}</td>
                    <td className="p-2">{String(r.eventType)}</td>
                    <td className="p-2">
                      <ResultBadge
                        result={normalizeAuditResult(String(r.result))}
                      />
                    </td>
                    <td className="p-2">{r.ip ?? ""}</td>
                    <td className="p-2 font-mono">{r.eventFingerprint}</td>
                    <td className="p-2 font-mono break-all">
                      {r.correlationId}
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
        <div className="text-sm text-gray-600">No results found.</div>
      )}
    </div>
  );
}
