"use client";

import { useEffect, useState } from "react";
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
  const [savingStream, setSavingStream] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);

    try {
      const data = await apiFetch<AuditRetentionPolicyDTO[]>(
        "/api/audit/retention"
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

  async function upsert(
    streamName: string,
    retentionDays: number | null,
    archiveEnabled: boolean
  ) {
    setSavingStream(streamName);

    try {
      await apiFetch<AuditRetentionPolicyDTO>(
        `/api/audit/retention/${streamName}`,
        {
          method: "PUT",
          body: JSON.stringify({
            retentionDays,
            archiveEnabled,
          }),
        }
      );

      toast.success("Retention policy updated");
      await load();
    } catch (e) {
      if (e instanceof ApiError) {
        toast.error(e.message);
      } else {
        toast.error("Update failed");
      }
    } finally {
      setSavingStream(null);
    }
  }

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-lg font-semibold">Retention Policies</h1>

      {loading && (
        <div className="text-sm text-gray-600">Loading policies...</div>
      )}

      {error && <div className="text-sm text-red-600">{error}</div>}

      <div className="overflow-auto border rounded">
        <table className="min-w-full text-xs">
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
              <RetentionRow
                key={p.streamName}
                policy={p}
                onSave={upsert}
                saving={savingStream === p.streamName}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function RetentionRow({
  policy,
  onSave,
  saving,
}: {
  policy: AuditRetentionPolicyDTO;
  onSave: (
    streamName: string,
    retentionDays: number | null,
    archiveEnabled: boolean
  ) => void;
  saving: boolean;
}) {
  const [retentionDays, setRetentionDays] = useState<number | null>(
    policy.retentionDays
  );
  const [archiveEnabled, setArchiveEnabled] = useState<boolean>(
    policy.archiveEnabled
  );
  const [localError, setLocalError] = useState<string | null>(null);

  return (
    <tr>
      <td className="p-2 font-mono">{policy.streamName}</td>

      <td className="p-2">
        <input
          type="number"
          min={1}
          value={retentionDays ?? ""}
          disabled={saving}
          onChange={(e) => {
            const value = e.target.value;

            if (value === "") {
              setRetentionDays(null);
              setLocalError(null);
              return;
            }

            const parsed = Number(value);

            if (!Number.isInteger(parsed) || parsed < 1) {
              setLocalError("Retention must be >= 1 day");
            } else {
              setLocalError(null);
              setRetentionDays(parsed);
            }
          }}
          className="border rounded px-2 py-1 w-24"
        />
        {localError && (
          <div className="text-xs text-red-600 mt-1">{localError}</div>
        )}
      </td>

      <td className="p-2">
        <input
          type="checkbox"
          checked={archiveEnabled}
          disabled={saving}
          onChange={(e) => setArchiveEnabled(e.target.checked)}
        />
      </td>

      <td className="p-2">
        <button
          disabled={saving || !!localError}
          onClick={() =>
            onSave(policy.streamName, retentionDays, archiveEnabled)
          }
          className="px-3 py-1 rounded bg-black text-white text-xs disabled:opacity-50"
        >
          {saving ? "Saving..." : "Save"}
        </button>
      </td>
    </tr>
  );
}