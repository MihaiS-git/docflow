"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";

import Button from "@/components/ui/Button";
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeaderCell,
  TableRow,
} from "@/components/ui/table";
import { apiFetch } from "@/lib/apiFetch";

type AuditSigningKeyPublicDTO = {
  keyId: string;
  fingerprintSha256Hex: string;
  createdAt: string;
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
        "/api/security/audit-keys",
      );
      setKeys(data);
    } catch (e) {
      toast.error(
        e instanceof Error ? e.message : "Failed to load audit signing keys",
      );
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  function handleDownload(keyId: string) {
    window.location.href = `/api/security/audit-keys/${encodeURIComponent(
      keyId,
    )}/public`;
  }

  return (
    <section className="rounded-md border border-(--color-border) bg-(--color-surface) p-4">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-semibold text-(--color-text-primary)">
            Audit Export Public Signing Keys
          </h2>
          <p className="mt-1 text-xs text-(--color-text-muted)">
            These public keys are required to verify exported audit JSONL files.
            Match <span className="font-mono">keyId</span> and{" "}
            <span className="font-mono">publicKeyFingerprint</span> from the
            export metadata with the list below.
          </p>
        </div>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={refresh}
          disabled={loading}
        >
          {loading ? "Refreshing…" : "Refresh"}
        </Button>
      </div>

      <TableContainer>
        <Table>
          <TableHead>
            <TableRow>
              <TableHeaderCell>keyId</TableHeaderCell>
              <TableHeaderCell>createdAt</TableHeaderCell>
              <TableHeaderCell>fingerprint (SHA-256)</TableHeaderCell>
              <TableHeaderCell>download</TableHeaderCell>
            </TableRow>
          </TableHead>

          <TableBody>
            {keys.length === 0 && (
              <TableRow>
                <TableCell colSpan={4} className="text-(--color-text-muted)">
                  {loading ? "Loading…" : "No signing keys found."}
                </TableCell>
              </TableRow>
            )}

            {keys.map((k) => (
              <TableRow
                key={k.keyId}
                className="hover:bg-(--color-table-row-hover)"
              >
                <TableCell className="font-mono">{k.keyId}</TableCell>
                <TableCell>{formatTs(k.createdAt)}</TableCell>
                <TableCell className="break-all font-mono">
                  {k.fingerprintSha256Hex}
                </TableCell>
                <TableCell>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => handleDownload(k.keyId)}
                  >
                    Download PEM
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </section>
  );
}