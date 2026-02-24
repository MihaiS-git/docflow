"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { apiFetch } from "@/lib/apiFetch";

type AuditSigningKeyPublicDTO = {
  keyId: string;
  fingerprintSha256Hex: string;
  createdAt: string; // ISO
};

function formatTs(iso: string): string {
  try {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
  } catch {
    return iso;
  }
}

export default function AuditKeysClient() {
  const [keys, setKeys] = useState<AuditSigningKeyPublicDTO[]>([]);
  const [loading, setLoading] = useState(false);

  async function refresh() {
    setLoading(true);
    try {
      const data = await apiFetch<AuditSigningKeyPublicDTO[]>(
        "/api/security/audit-keys"
      );
      setKeys(data);
    } catch (e) {
      toast.error(
        e instanceof Error ? e.message : "Failed to load audit signing keys"
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  function handleDownload(keyId: string) {
    // Let browser handle Content-Disposition
    window.location.href = `/api/security/audit-keys/${encodeURIComponent(
      keyId
    )}/public`;
  }

  return (
    <div className="p-4 space-y-6">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div>
          <h1 className="text-lg font-semibold">
            Audit Export Public Signing Keys
          </h1>
          <p className="text-sm text-gray-600">
            These public keys are required to verify exported audit JSONL
            files. Match <span className="font-mono">keyId</span> and{" "}
            <span className="font-mono">publicKeyFingerprint</span> from the
            export metadata with the list below.
          </p>
        </div>

        <button
          type="button"
          onClick={refresh}
          disabled={loading}
          className="px-3 py-1 rounded border disabled:opacity-50"
        >
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      <div className="border rounded overflow-auto">
        <table className="min-w-full text-xs">
          <thead>
            <tr>
              <th className="p-2 border-b text-left">keyId</th>
              <th className="p-2 border-b text-left">createdAt</th>
              <th className="p-2 border-b text-left">fingerprint (SHA-256)</th>
              <th className="p-2 border-b text-left">download</th>
            </tr>
          </thead>
          <tbody>
            {keys.length === 0 && (
              <tr>
                <td className="p-2 text-gray-600" colSpan={4}>
                  {loading ? "Loading…" : "No signing keys found."}
                </td>
              </tr>
            )}

            {keys.map((k) => (
              <tr key={k.keyId}>
                <td className="p-2 font-mono">{k.keyId}</td>
                <td className="p-2">{formatTs(k.createdAt)}</td>
                <td className="p-2 font-mono break-all">
                  {k.fingerprintSha256Hex}
                </td>
                <td className="p-2">
                  <button
                    type="button"
                    className="px-3 py-1 rounded border"
                    onClick={() => handleDownload(k.keyId)}
                  >
                    Download PEM
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}