"use client";

import { useMemo, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { downloadAuditFile } from "@/lib/audit/auditDownload";
import { buildRangeQueryParams } from "@/lib/audit/auditRange";
import { useCursorPagination } from "@/lib/audit/useCursorPagination";
import { AuditRangePanel } from "@/lib/audit/AuditRangePanel";
import { AuditExportButtons } from "@/lib/audit/AuditExportButtons";
import { AuditVerifyPanel } from "@/lib/audit/AuditVerifyPanel";
import { AuditCursorPagination } from "@/lib/audit/AuditCursorPagination";
import {
  toDateTimeLocalString,
  formatAuditTimestamp,
} from "@/lib/date/dateTimeLocal";
import { CredentialLifecycleAuditCursorPageDTO } from "@/types/api/CredentialLifecycleAuditCursorPageDTO";
import { CredentialLifecycleAuditRow } from "@/types/api/CredentialLifecycleAuditRow";

type UiVerifyResultDTO = {
  ok: boolean;
  verifiedCount: number;
  failedCount: number;
  from: string;
  to: string;
  message?: string;
};

type CredentialLifecycleAuditVerificationResultDTO = {
  valid: boolean;
  verifiedCount: number;
  failedEventId: string | null;
  failureReason: string | null;
};

function adaptCursorPage(dto: CredentialLifecycleAuditCursorPageDTO) {
  return {
    content: dto.items ?? [],
    hasNext: dto.hasMore ?? false,
    nextCursorTimestamp: dto.nextCursorTimestamp ?? undefined,
    nextCursorId: dto.nextCursorId ?? undefined,
  };
}

export default function CredentialLifecycleAuditClient() {
  const now = useMemo(() => new Date(), []);
  const oneHourAgo = useMemo(() => new Date(now.getTime() - 3600000), [now]);

  const [from, setFrom] = useState(toDateTimeLocalString(oneHourAgo));
  const [to, setTo] = useState(toDateTimeLocalString(now));
  const [size, setSize] = useState(20);

  // Filters supported by controller:
  // correlationId, subjectExternalId, result
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

  const [verifyResult, setVerifyResult] = useState<UiVerifyResultDTO | null>(
    null
  );

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
        `/api/audit/credential-lifecycle?${buildParams().toString()}`
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
          nextCursorId
        ).toString()}`
      );

      appendPage(adaptCursorPage(data));
    } finally {
      setLoadingMore(false);
    }
  }

  async function handleVerify() {
    setVerifying(true);

    try {
      const qs = buildRangeQueryParams({ from, to });

      const data =
        await apiFetch<CredentialLifecycleAuditVerificationResultDTO>(
          `/api/audit/credential-lifecycle/verify?${qs.toString()}`
        );

      const ui: UiVerifyResultDTO = {
        ok: Boolean(data.valid),
        verifiedCount: Number(data.verifiedCount ?? 0),
        failedCount: data.failedEventId ? 1 : 0,
        from: qs.get("from") ?? "",
        to: qs.get("to") ?? "",
        message: data.failureReason ?? undefined,
      };

      setVerifyResult(ui);
    } finally {
      setVerifying(false);
    }
  }

  async function handleExportJsonl() {
    setDownloading("jsonl");
    setError(null);

    try {
      const qs = buildRangeQueryParams({ from, to });

      await downloadAuditFile({
        path: `/api/audit/credential-lifecycle/export?${qs.toString()}`,
        filename: "credential-lifecycle-audit-export.jsonl",
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Export failed");
    } finally {
      setDownloading(null);
    }
  }

  async function handleExportCsv() {
    setDownloading("csv");
    setError(null);

    try {
      const qs = buildRangeQueryParams({ from, to });

      await downloadAuditFile({
        path: `/api/audit/credential-lifecycle/export/csv?${qs.toString()}`,
        filename: "credential-lifecycle-audit-export.csv",
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Export failed");
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
                    <td className="p-2">{String(r.result)}</td>
                    <td className="p-2">{r.ip ?? ""}</td>
                    <td className="p-2 font-mono">{r.eventFingerprint}</td>
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
