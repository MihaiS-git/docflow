"use client";

import { memo, useCallback, useEffect, useRef, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { toast } from "sonner";

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
      setError(e instanceof Error ? e.message : "Failed to load");
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
    <div className="p-6 space-y-6">
      <h1 className="text-lg font-semibold">Retention Policies</h1>

      {loading && (
        <div className="text-sm text-gray-600">Loading policies...</div>
      )}

      {error && <div className="text-sm text-red-600">{error}</div>}

      <div className="overflow-auto border rounded">
        <table className="min-w-full text-xs table-fixed">
          <thead>
            <tr>
              <th className="p-2 border-b text-left">Stream</th>
              <th className="p-2 border-b text-left">Retention Days</th>
              <th className="p-2 border-b text-left">Archive</th>
              <th className="p-2 border-b text-left">Action</th>
            </tr>
          </thead>
          <tbody>
            {policies.map((p) => (
              <RetentionRow key={p.streamName} policy={p} onSave={upsert} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
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
    <tr>
      <td className="p-2 font-mono">{policy.streamName}</td>

      <td className="p-2">
        <input
          type="number"
          min={1}
          step={1}
          defaultValue={policy.retentionDays ?? ""}
          ref={retentionRef}
          className="border rounded px-2 py-1 w-24"
        />
        {localError && (
          <div className="text-xs text-red-600 mt-1">{localError}</div>
        )}
      </td>

      <td className="p-2">
        <input
          type="checkbox"
          defaultChecked={policy.archiveEnabled}
          disabled={saving}
          ref={archiveRef}
        />
      </td>

      <td className="p-2">
        <button
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
          className="w-20 px-3 py-1 rounded bg-black text-white text-xs disabled:opacity-50"
        >
          {saving ? "..." : "Save"}
        </button>
      </td>
    </tr>
  );
});
