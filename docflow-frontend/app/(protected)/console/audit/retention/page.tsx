"use client";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";

import { memo, useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { toast } from "sonner";

import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeaderCell,
  TableRow,
} from "@/components/ui/table";
import Button from "@/components/ui/Button";
import { getErrorMessage } from "@/lib/api/getErrorMessage";

type AuditRetentionPolicyDTO = {
  streamName: string;
  retentionDays: number | null;
  archiveEnabled: boolean;
  createdAt: string | null;
  updatedAt: string | null;
};

export default function RetentionPolicyPage() {
  const [policies, setPolicies] = useState<AuditRetentionPolicyDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);

    try {
      const data = await apiFetch<AuditRetentionPolicyDTO[]>(
        "/api/audit/retention",
      );
      setPolicies(data);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const upsert = useCallback(
    async (
      streamName: string,
      retentionDays: number | null,
      archiveEnabled: boolean,
    ) => {
      try {
        await apiFetch<AuditRetentionPolicyDTO>(
          `/api/audit/retention/${streamName}`,
          {
            method: "PUT",
            body: JSON.stringify({
              retentionDays,
              archiveEnabled,
            }),
          },
        );

        toast.success("Retention policy updated");

        setPolicies((prev) =>
          prev.map((p) =>
            p.streamName === streamName
              ? { ...p, retentionDays, archiveEnabled }
              : p,
          ),
        );
      } catch (e) {
        if (e instanceof ApiError) {
          toast.error(e.message);
        } else {
          toast.error("Update failed");
        }
      }
    },
    [],
  );

  return (
    <PageContainer>
      <PageHeader
        title="Audit — Retention Policies"
        description="Define retention and archiving rules for each audit stream."
      />

      <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
        {loading && (
          <div className="text-sm text-(--color-text-muted)">
            Loading policies…
          </div>
        )}

        {error && (
          <div className="text-sm text-(--color-error)">{error}</div>
        )}

        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableHeaderCell>Stream</TableHeaderCell>
                <TableHeaderCell>Retention Days</TableHeaderCell>
                <TableHeaderCell>Archive</TableHeaderCell>
                <TableHeaderCell>Action</TableHeaderCell>
              </TableRow>
            </TableHead>

            <TableBody>
              {policies.map((p) => (
                <RetentionRow key={p.streamName} policy={p} onSave={upsert} />
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </section>
    </PageContainer>
  );
}

const RetentionRow = memo(function RetentionRow({
  policy,
  onSave,
}: {
  policy: AuditRetentionPolicyDTO;
  onSave: (
    streamName: string,
    retentionDays: number | null,
    archiveEnabled: boolean,
  ) => Promise<void> | void;
}) {
  const retentionRef = useRef<HTMLInputElement>(null);
  const archiveRef = useRef<HTMLInputElement>(null);

  const [localError, setLocalError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  return (
    <TableRow className="hover:bg-(--color-table-row-hover)">
      <TableCell className="font-mono">{policy.streamName}</TableCell>

      <TableCell>
        <input
          type="number"
          min={1}
          step={1}
          defaultValue={policy.retentionDays ?? ""}
          ref={retentionRef}
          className="w-24 rounded border border-(--color-border) bg-(--color-surface) px-2 py-1 text-(--color-text-primary)"
        />
        {localError && (
          <div className="mt-1 text-xs text-(--color-error)">
            {localError}
          </div>
        )}
      </TableCell>

      <TableCell>
        <input
          type="checkbox"
          defaultChecked={policy.archiveEnabled}
          disabled={saving}
          ref={archiveRef}
        />
      </TableCell>

      <TableCell>
        <Button
          size="sm"
          variant="primary"
          disabled={saving || !!localError}
          onClick={async () => {
            const value = retentionRef.current?.value ?? "";

            if (value !== "") {
              const parsed = Number(value);

              if (!Number.isInteger(parsed) || parsed < 1) {
                setLocalError("Retention must be >= 1 day");
                return;
              }
            }

            setLocalError(null);
            setSaving(true);

            try {
              const retention = value === "" ? null : Number(value);
              const archiveEnabled = archiveRef.current?.checked ?? false;

              await onSave(policy.streamName, retention, archiveEnabled);
            } finally {
              setSaving(false);
            }
          }}
        >
          {saving ? "…" : "Save"}
        </Button>
      </TableCell>
    </TableRow>
  );
});