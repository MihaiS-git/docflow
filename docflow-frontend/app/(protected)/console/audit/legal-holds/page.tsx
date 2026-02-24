"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import { ApiError } from "@/lib/apiErrors";
import { toast } from "sonner";

type AuditLegalHoldDTO = {
  id: string;
  streamName: string;
  eventId: string | null;
  correlationId: string | null;
  caseReferenceId: string;
  reason: string;
  createdBy: string;
  createdAt: string;
  active: boolean;
};

export default function LegalHoldPage() {
  const [rows, setRows] = useState<AuditLegalHoldDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deactivatingId, setDeactivatingId] = useState<string | null>(null);

  // Filters
  const [streamName, setStreamName] = useState("");
  const [caseReferenceId, setCaseReferenceId] = useState("");
  const [correlationId, setCorrelationId] = useState("");
  const [createdBy, setCreatedBy] = useState("");
  const [active, setActive] = useState<string>("");

  function buildQuery() {
    const params = new URLSearchParams();
    params.set("page", "0");
    params.set("size", "50");

    if (streamName.trim()) params.set("streamName", streamName.trim());
    if (caseReferenceId.trim())
      params.set("caseReferenceId", caseReferenceId.trim());
    if (correlationId.trim())
      params.set("correlationId", correlationId.trim());
    if (createdBy.trim()) params.set("createdBy", createdBy.trim());
    if (active === "true" || active === "false")
      params.set("active", active);

    return params.toString();
  }

  async function load() {
    setLoading(true);
    setError(null);

    try {
      const page = await apiFetch<{
        content: AuditLegalHoldDTO[];
      }>(`/api/admin/audit/legal-holds?${buildQuery()}`);

      setRows(page.content);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load legal holds");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function deactivate(id: string) {
    if (!confirm("Deactivate this legal hold?")) return;

    setDeactivatingId(id);

    try {
      await apiFetch(`/api/admin/audit/legal-holds/${id}/deactivate`, {
        method: "POST",
      });

      toast.success("Legal hold deactivated");
      await load();
    } catch (e) {
      if (e instanceof ApiError) {
        toast.error(e.message);
      } else {
        toast.error("Operation failed");
      }
    } finally {
      setDeactivatingId(null);
    }
  }

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-lg font-semibold">Legal Holds</h1>

      {/* Filters */}
      <div className="border rounded p-3 grid gap-3 md:grid-cols-5">
        <input
          className="border rounded px-2 py-1"
          placeholder="Stream"
          value={streamName}
          onChange={(e) => setStreamName(e.target.value)}
        />
        <input
          className="border rounded px-2 py-1"
          placeholder="Case Ref"
          value={caseReferenceId}
          onChange={(e) => setCaseReferenceId(e.target.value)}
        />
        <input
          className="border rounded px-2 py-1"
          placeholder="Correlation ID"
          value={correlationId}
          onChange={(e) => setCorrelationId(e.target.value)}
        />
        <input
          className="border rounded px-2 py-1"
          placeholder="Created By"
          value={createdBy}
          onChange={(e) => setCreatedBy(e.target.value)}
        />
        <select
          className="border rounded px-2 py-1"
          value={active}
          onChange={(e) => setActive(e.target.value)}
        >
          <option value="">All</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
      </div>

      <div>
        <button
          onClick={load}
          disabled={loading}
          className="px-3 py-1 rounded bg-black text-white text-xs disabled:opacity-50"
        >
          {loading ? "Loading..." : "Refresh"}
        </button>
      </div>

      {error && <div className="text-sm text-red-600">{error}</div>}

      <div className="overflow-auto border rounded">
        <table className="min-w-full text-xs">
          <thead>
            <tr>
              <th className="p-2 border-b">Stream</th>
              <th className="p-2 border-b">Case Ref</th>
              <th className="p-2 border-b">Correlation</th>
              <th className="p-2 border-b">Reason</th>
              <th className="p-2 border-b">Created By</th>
              <th className="p-2 border-b">Active</th>
              <th className="p-2 border-b">Action</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td className="p-2 font-mono">{r.streamName}</td>
                <td className="p-2">{r.caseReferenceId}</td>
                <td className="p-2 font-mono break-all">
                  {r.correlationId ?? "-"}
                </td>
                <td className="p-2">{r.reason}</td>
                <td className="p-2 font-mono">{r.createdBy}</td>
                <td className="p-2">
                  {r.active ? "ACTIVE" : "INACTIVE"}
                </td>
                <td className="p-2">
                  <button
                    disabled={!r.active || deactivatingId === r.id}
                    onClick={() => deactivate(r.id)}
                    className="px-3 py-1 rounded bg-red-600 text-white text-xs disabled:opacity-50"
                  >
                    {deactivatingId === r.id
                      ? "Deactivating..."
                      : "Deactivate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {loading && (
          <div className="p-3 text-sm text-gray-600">
            Loading legal holds...
          </div>
        )}

        {!loading && rows.length === 0 && !error && (
          <div className="p-3 text-sm text-gray-600">
            No legal holds found.
          </div>
        )}
      </div>
    </div>
  );
}