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
import { AuditVerificationResultDTO } from "@/lib/api/AuditVerificationResultDTO";
import { SensitiveAccessAuditRow } from "@/types/api/SensitiveAccessAuditRow";
import { SensitiveAccessAuditCursorPageDTO } from "@/types/api/SensitiveAccessAuditCursorPageDTO";

export default function SensitiveAccessAuditClient() {
  const { from, to, size, setFrom, setTo, setSize } = useDefaultAuditRange();

  const [correlationId, setCorrelationId] = useState("");
  const [subjectId, setSubjectId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [actorUserId, setActorUserId] = useState("");

  const {
    rows,
    nextCursorTimestamp,
    nextCursorId,
    hasNext,
    reset,
    applyFirstPage,
    appendPage,
  } = useCursorPagination<SensitiveAccessAuditRow>();

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
    if (subjectId.trim()) qs.set("subjectId", subjectId.trim());
    if (tenantId.trim()) qs.set("tenantId", tenantId.trim());
    if (actorUserId.trim()) qs.set("actorUserId", actorUserId.trim());

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

      const data = await apiFetch<SensitiveAccessAuditCursorPageDTO>(
        `/api/audit/sensitive-access?${buildParams().toString()}`,
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
      const data = await apiFetch<SensitiveAccessAuditCursorPageDTO>(
        `/api/audit/sensitive-access?${buildParams(
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
    "/api/audit/sensitive-access",
    from,
    to,
    setVerifyResult,
    setVerifying,
  );

  async function handleExportJsonl() {
    setDownloading("jsonl");
    try {
      const qs = buildRangeQueryParams({ from, to });

      if (correlationId.trim()) qs.set("correlationId", correlationId.trim());
      if (subjectId.trim()) qs.set("subjectId", subjectId.trim());
      if (tenantId.trim()) qs.set("tenantId", tenantId.trim());
      if (actorUserId.trim()) qs.set("actorUserId", actorUserId.trim());

      await downloadAuditFile({
        path: `/api/audit/sensitive-access/export?${qs.toString()}`,
        filename: "sensitive-access-export.jsonl",
      });
    } finally {
      setDownloading(null);
    }
  }

  async function handleExportCsv() {
    setDownloading("csv");
    try {
      const qs = buildRangeQueryParams({ from, to });

      if (correlationId.trim()) qs.set("correlationId", correlationId.trim());
      if (subjectId.trim()) qs.set("subjectId", subjectId.trim());
      if (tenantId.trim()) qs.set("tenantId", tenantId.trim());
      if (actorUserId.trim()) qs.set("actorUserId", actorUserId.trim());

      await downloadAuditFile({
        path: `/api/audit/sensitive-access/export/csv?${qs.toString()}`,
        filename: "sensitive-access-export.csv",
      });
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="p-4 space-y-6">
      <h1 className="text-lg font-semibold">Sensitive Access Audit</h1>

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

      <div className="border rounded p-3 grid gap-3 md:grid-cols-4">
        <input
          className="border px-2 py-1 rounded"
          placeholder="Correlation ID"
          value={correlationId}
          onChange={(e) => setCorrelationId(e.target.value)}
        />
        <input
          className="border px-2 py-1 rounded"
          placeholder="Subject ID"
          value={subjectId}
          onChange={(e) => setSubjectId(e.target.value)}
        />
        <input
          className="border px-2 py-1 rounded"
          placeholder="Tenant ID"
          value={tenantId}
          onChange={(e) => setTenantId(e.target.value)}
        />
        <input
          className="border px-2 py-1 rounded"
          placeholder="Actor User ID"
          value={actorUserId}
          onChange={(e) => setActorUserId(e.target.value)}
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
                  <th className="p-2 border-b">tenant</th>
                  <th className="p-2 border-b">actor</th>
                  <th className="p-2 border-b">subject</th>
                  <th className="p-2 border-b">resource</th>
                  <th className="p-2 border-b">action</th>
                  <th className="p-2 border-b">classification</th>
                  <th className="p-2 border-b">result</th>
                  <th className="p-2 border-b">correlationId</th>
                  <th className="p-2 border-b">fingerprint</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.eventFingerprint}>
                    <td className="p-2 font-mono">
                      {formatAuditTimestamp(r.timestamp)}
                    </td>

                    <td className="p-2 font-mono">{r.tenantId ?? "-"}</td>

                    <td className="p-2 font-mono">{r.actorUserId ?? "-"}</td>

                    <td className="p-2 font-mono">{r.subjectId}</td>

                    <td className="p-2">{r.resource}</td>

                    <td className="p-2">{r.action}</td>

                    <td className="p-2">{r.dataClassification}</td>

                    <td className="p-2">{r.result}</td>

                    <td className="p-2 font-mono break-all">
                      {r.correlationId}
                    </td>

                    <td className="p-2 font-mono break-all">
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
        <div className="text-sm text-gray-600">No results found.</div>
      )}
    </div>
  );
}
