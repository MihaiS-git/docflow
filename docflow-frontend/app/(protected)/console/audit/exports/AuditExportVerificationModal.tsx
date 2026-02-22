"use client";

import { useMemo, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";
import type { AuditExportSnapshotDTO } from "@/types/api/AuditExportSnapshotDTO";
import type { AuditExportVerificationResultDTO } from "@/types/api/AuditExportVerificationResultDTO";

type Props = {
  open: boolean;
  snapshot: AuditExportSnapshotDTO | null;
  onClose: () => void;
};

type SubmitState = "idle" | "uploading" | "done";

function boolClass(v: boolean) {
  return v ? "text-green-700 font-semibold" : "text-red-700 font-semibold";
}

function BoolRow({
  label,
  value,
}: {
  label: string;
  value: boolean;
}) {
  return (
    <div className="flex justify-between">
      <span>{label}</span>
      <span className={boolClass(value)}>{String(value)}</span>
    </div>
  );
}

export function AuditExportVerificationModal({
  open,
  snapshot,
  onClose,
}: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [submitState, setSubmitState] = useState<SubmitState>("idle");
  const [result, setResult] =
    useState<AuditExportVerificationResultDTO | null>(null);
  const [error, setError] = useState<string | null>(null);

  const disabled = submitState === "uploading";
  const snapshotId = snapshot?.id ?? null;

  const title = useMemo(() => {
    if (!snapshot) return "Verify export snapshot";
    return `Verify snapshot: ${snapshot.stream}`;
  }, [snapshot]);

  function resetState() {
    setFile(null);
    setSubmitState("idle");
    setResult(null);
    setError(null);
  }

  function handleClose() {
    if (!disabled) {
      resetState();
      onClose();
    }
  }

  if (!open || !snapshot) return null;

  async function onSubmit() {
    if (!snapshotId) return;

    setError(null);
    setResult(null);

    if (!file) {
      setError("Select a .jsonl file first.");
      return;
    }

    if (!file.name.toLowerCase().endsWith(".jsonl")) {
      setError("Only .jsonl files are accepted.");
      return;
    }

    setSubmitState("uploading");

    try {
      const fd = new FormData();
      fd.append("file", file);

      const res = await apiFetch<AuditExportVerificationResultDTO>(
        `/api/audit/exports/${snapshotId}/verify`,
        {
          method: "POST",
          body: fd,
        },
      );

      setResult(res);
      setSubmitState("done");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Verification failed");
      setSubmitState("idle");
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Verify audit export snapshot"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) handleClose();
      }}
    >
      <div className="w-full max-w-3xl rounded border bg-white shadow">
        <div className="flex items-start justify-between gap-3 border-b p-3">
          <div className="space-y-1">
            <h2 className="text-base font-semibold">{title}</h2>
            <p className="text-xs text-neutral-600">
              Snapshot ID: <span className="font-mono">{snapshot.id}</span>
            </p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            disabled={disabled}
            className="rounded border px-2 py-1 text-sm disabled:opacity-50"
          >
            Close
          </button>
        </div>

        <div className="space-y-6 p-4 text-sm">

          {/* Snapshot Context */}
          <section className="rounded border p-4 space-y-2">
            <h3 className="font-semibold">Snapshot Context</h3>

            <div>
              <span className="text-neutral-600">Created:</span>{" "}
              {formatAuditTimestamp(snapshot.createdAt)}
            </div>

            <div>
              <span className="text-neutral-600">Stream:</span>{" "}
              {snapshot.stream}
            </div>

            <div>
              <span className="text-neutral-600">Tenant:</span>{" "}
              {snapshot.tenantId ?? "GLOBAL"}
            </div>

            <div>
              <span className="text-neutral-600">Export range:</span>{" "}
              {formatAuditTimestamp(snapshot.fromTs)} →{" "}
              {formatAuditTimestamp(snapshot.toTs)}
            </div>

            <div>
              <span className="text-neutral-600">Row count:</span>{" "}
              {snapshot.rowCount}
            </div>

            <div>
              <span className="text-neutral-600">Key ID:</span>{" "}
              {snapshot.keyId ?? "—"}
            </div>

            <div>
              <span className="text-neutral-600">Signature algorithm:</span>{" "}
              {snapshot.signatureAlg}
            </div>
          </section>

          {/* Upload */}
          <section className="space-y-3">
            <h3 className="font-semibold">Upload JSONL</h3>

            <input
              type="file"
              accept=".jsonl,application/x-ndjson"
              disabled={disabled}
              className="block w-full rounded border px-2 py-2 disabled:opacity-50"
              onChange={(e) =>
                setFile(e.target.files?.item(0) ?? null)
              }
            />

            <button
              type="button"
              onClick={onSubmit}
              disabled={disabled}
              className="rounded bg-black px-3 py-1.5 text-white disabled:opacity-50"
            >
              {submitState === "uploading"
                ? "Verifying…"
                : "Submit verification"}
            </button>

            {error && (
              <p role="alert" className="text-red-700">
                {error}
              </p>
            )}
          </section>

          {/* Result */}
          {result && (
            <section className="rounded border p-4 space-y-3">
              <div className="flex justify-between items-center">
                <h3 className="font-semibold">Verification Result</h3>
                <span className={boolClass(result.ok)}>
                  {result.ok ? "OK" : "FAILED"}
                </span>
              </div>

              <div className="grid gap-1">
                <BoolRow label="digestMatches" value={result.digestMatches} />
                <BoolRow label="keyIdExists" value={result.keyIdExists} />
                <BoolRow label="signatureValid" value={result.signatureValid} />

                <BoolRow label="metaPresent" value={result.metaPresent} />
                <BoolRow label="metaParsed" value={result.metaParsed} />
                <BoolRow label="metaSnapshotIdMatches" value={result.metaSnapshotIdMatches} />
                <BoolRow label="metaDigestMatches" value={result.metaDigestMatches} />
                <BoolRow label="metaSignatureMatches" value={result.metaSignatureMatches} />
                <BoolRow label="metaKeyIdMatches" value={result.metaKeyIdMatches} />
                <BoolRow label="metaAlgorithmMatches" value={result.metaAlgorithmMatches} />
                <BoolRow label="metaDigestAlgorithmMatches" value={result.metaDigestAlgorithmMatches} />
                <BoolRow label="metaSignatureInputMatches" value={result.metaSignatureInputMatches} />
                <BoolRow label="metaStreamMatches" value={result.metaStreamMatches} />
                <BoolRow label="metaRangeMatches" value={result.metaRangeMatches} />
                <BoolRow label="metaTenantMatches" value={result.metaTenantMatches} />
                <BoolRow label="metaRowCountMatches" value={result.metaRowCountMatches} />
              </div>

              <div>
                <span className="font-medium">Message:</span>{" "}
                {result.message}
              </div>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}
