"use client";

import { useMemo, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { formatAuditTimestamp } from "@/lib/date/dateTimeLocal";

import type { AuditExportSnapshotDTO } from "@/types/api/AuditExportSnapshotDTO";
import type { AuditExportVerificationResultDTO } from "@/types/api/AuditExportVerificationResultDTO";

import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import { DataBlockHeader } from "@/components/ui/DataBlockHeader";

type Props = {
  open: boolean;
  snapshot: AuditExportSnapshotDTO | null;
  onClose: () => void;
};

type SubmitState = "idle" | "uploading" | "done";

function boolClass(v: boolean) {
  return v
    ? "text-(--color-success) font-semibold"
    : "text-(--color-error) font-semibold";
}

function BoolRow({ label, value }: { label: string; value: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-(--color-text-secondary) truncate">{label}</span>
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
  const [result, setResult] = useState<AuditExportVerificationResultDTO | null>(
    null,
  );
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
      <div className="w-full max-w-3xl">
        <Card
          padding="none"
          className="flex max-h-[85vh] flex-col overflow-hidden"
        >
          {/* Header */}
          <div className="flex items-start justify-between gap-3 border-b border-(--color-border) p-4">
            <div className="space-y-1">
              <h2 className="text-base font-semibold text-(--color-text-primary)">
                {title}
              </h2>
              <p className="text-xs text-(--color-text-muted)">
                Snapshot ID: <span className="font-mono">{snapshot.id}</span>
              </p>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={handleClose}
              disabled={disabled}
            >
              Close
            </Button>
          </div>

          {/* Scrollable Body */}
          <div className="flex-1 overflow-y-auto space-y-6 p-4 text-sm">
            {/* Snapshot Context */}
            <section className="space-y-3">
              <DataBlockHeader title="Snapshot Context" />

              <Card className="space-y-2">
                <div>
                  <span className="text-(--color-text-muted)">Timestamp:</span>{" "}
                  {formatAuditTimestamp(snapshot.timestamp)}
                </div>

                <div>
                  <span className="text-(--color-text-muted)">Stream:</span>{" "}
                  {snapshot.stream}
                </div>

                <div>
                  <span className="text-(--color-text-muted)">Tenant:</span>{" "}
                  {snapshot.tenantId ?? "GLOBAL"}
                </div>

                <div>
                  <span className="text-(--color-text-muted)">
                    Export range:
                  </span>{" "}
                  {formatAuditTimestamp(snapshot.fromTs)} →{" "}
                  {formatAuditTimestamp(snapshot.toTs)}
                </div>

                <div>
                  <span className="text-(--color-text-muted)">Row count:</span>{" "}
                  {snapshot.rowCount}
                </div>

                <div>
                  <span className="text-(--color-text-muted)">Key ID:</span>{" "}
                  {snapshot.keyId ?? "—"}
                </div>

                <div>
                  <span className="text-(--color-text-muted)">
                    Signature algorithm:
                  </span>{" "}
                  {snapshot.signatureAlg}
                </div>
              </Card>
            </section>

            {/* Upload */}
            <section className="space-y-3">
              <DataBlockHeader title="Upload JSONL" />

              <Input
                type="file"
                accept=".jsonl,application/x-ndjson"
                disabled={disabled}
                onChange={(e) => setFile(e.target.files?.item(0) ?? null)}
              />

              <div className="flex justify-end">
                <Button
                  onClick={onSubmit}
                  loading={submitState === "uploading"}
                  disabled={disabled}
                >
                  Submit verification
                </Button>
              </div>

              {error && (
                <p role="alert" className="text-(--color-error)">
                  {error}
                </p>
              )}
            </section>

            {/* Result */}
            {result && (
              <section className="space-y-3">
                <DataBlockHeader title="Verification Result" />

                <Card className="space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-(--color-text-primary)">
                      Status
                    </span>
                    <span className={boolClass(result.ok)}>
                      {result.ok ? "OK" : "FAILED"}
                    </span>
                  </div>

                  {/* ✅ GROUPED + SEPARATED */}
                  <div className="grid gap-4 md:grid-cols-2">
                    {/* Core integrity */}
                    <div className="space-y-2">
                      <div className="text-xs font-semibold uppercase tracking-wide text-(--color-text-muted)">
                        Core integrity
                      </div>

                      <div className="space-y-1">
                        <BoolRow
                          label="digestMatches"
                          value={result.digestMatches}
                        />
                        <BoolRow
                          label="keyIdExists"
                          value={result.keyIdExists}
                        />
                        <BoolRow
                          label="signatureValid"
                          value={result.signatureValid}
                        />
                      </div>
                    </div>

                    {/* Metadata validation */}
                    <div className="space-y-2 border-l border-(--color-border) pl-4">
                      <div className="text-xs font-semibold uppercase tracking-wide text-(--color-text-muted)">
                        Metadata validation
                      </div>

                      <div className="space-y-1">
                        <BoolRow
                          label="metaPresent"
                          value={result.metaPresent}
                        />
                        <BoolRow label="metaParsed" value={result.metaParsed} />
                        <BoolRow
                          label="metaSnapshotIdMatches"
                          value={result.metaSnapshotIdMatches}
                        />
                        <BoolRow
                          label="metaDigestMatches"
                          value={result.metaDigestMatches}
                        />
                        <BoolRow
                          label="metaSignatureMatches"
                          value={result.metaSignatureMatches}
                        />
                        <BoolRow
                          label="metaKeyIdMatches"
                          value={result.metaKeyIdMatches}
                        />
                        <BoolRow
                          label="metaAlgorithmMatches"
                          value={result.metaAlgorithmMatches}
                        />
                        <BoolRow
                          label="metaDigestAlgorithmMatches"
                          value={result.metaDigestAlgorithmMatches}
                        />
                        <BoolRow
                          label="metaSignatureInputMatches"
                          value={result.metaSignatureInputMatches}
                        />
                        <BoolRow
                          label="metaStreamMatches"
                          value={result.metaStreamMatches}
                        />
                        <BoolRow
                          label="metaRangeMatches"
                          value={result.metaRangeMatches}
                        />
                        <BoolRow
                          label="metaTenantMatches"
                          value={result.metaTenantMatches}
                        />
                        <BoolRow
                          label="metaRowCountMatches"
                          value={result.metaRowCountMatches}
                        />
                      </div>
                    </div>
                  </div>

                  <div>
                    <span className="font-medium text-(--color-text-primary)">
                      Message:
                    </span>{" "}
                    <span className="text-(--color-text-secondary)">
                      {result.message}
                    </span>
                  </div>
                </Card>
              </section>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}
