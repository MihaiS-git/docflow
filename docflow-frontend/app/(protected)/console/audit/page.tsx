"use client";

import { AUDIT_STREAMS } from "@/lib/audit/auditStreams";
import { useAuth } from "@/lib/auth/useAuth";
import Link from "next/link";
import { useMemo, useState } from "react";

import PageContainer from "@/components/layout/PageContainer";
import PageHeader from "@/components/layout/PageHeader";
import Card from "@/components/ui/Card";
import Input from "@/components/ui/Input";
import { DataBlockHeader } from "@/components/ui/DataBlockHeader";

export default function AuditConsolePage() {
  const { identity } = useAuth();
  const [query, setQuery] = useState("");

  const isAuditor = identity?.roles.includes("AUDITOR");

  const filteredStreams = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return AUDIT_STREAMS;

    return AUDIT_STREAMS.filter((s) =>
      s.name.toLowerCase().includes(q),
    );
  }, [query]);

  const grouped = useMemo(() => {
    return filteredStreams.reduce<Record<string, typeof filteredStreams>>(
      (acc, stream) => {
        acc[stream.category] ??= [];
        acc[stream.category].push(stream);
        return acc;
      },
      {},
    );
  }, [filteredStreams]);

  if (!isAuditor) {
    return (
      <PageContainer>
        <p className="text-(--color-text-secondary)">Access denied</p>
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <PageHeader
        title="Audit Console"
        description="Browse audit streams and manage audit integrity, exports, and retention."
      />

      {/* Search */}
      <div className="max-w-sm">
        <Input
          placeholder="Search audit streams..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      {/* Sections */}
      <div className="space-y-4 mt-4">
        {/* Streams label */}
        <div className="text-sm font-medium text-(--color-text-secondary)">
          Streams
        </div>

        {/* Audit Streams */}
        {Object.entries(grouped).map(([category, streams]) => (
          <Card key={category} className="p-0 overflow-hidden" padding="none">
            <DataBlockHeader title={category} />

            <div className="p-3 grid gap-2 md:grid-cols-2 lg:grid-cols-3">
              {streams.map((stream) => (
                <Link
                  key={stream.path}
                  href={`/console/audit/streams/${stream.path}`}
                  className="
                    block h-full rounded-md
                    bg-(--color-surface-alt)
                    px-3 py-2
                    transition
                    hover:bg-(--color-surface)
                  "
                >
                  <div className="text-sm font-medium text-(--color-text-primary)">
                    {stream.name}
                  </div>

                  <div className="text-xs text-(--color-text-secondary)">
                    Inspect events in this audit stream.
                  </div>
                </Link>
              ))}
            </div>
          </Card>
        ))}

        {/* Governance label */}
        <div className="text-sm font-medium text-(--color-text-secondary)">
          Governance
        </div>

        {/* Governance */}
        <Card className="p-0 overflow-hidden" padding="none">
          <DataBlockHeader title="Audit Integrity & Governance" />

          <div className="p-3 grid gap-2 md:grid-cols-2 lg:grid-cols-3">
            <Link
              href="/console/audit/retention"
              className="
                block h-full rounded-md
                bg-(--color-surface-alt)
                px-3 py-2
                transition
                hover:bg-(--color-surface)
              "
            >
              <div className="text-sm font-medium text-(--color-text-primary)">
                Retention Policies
              </div>
              <div className="text-xs text-(--color-text-secondary)">
                Configure retention and cleanup rules.
              </div>
            </Link>

            <Link
              href="/console/audit/exports"
              className="
                block h-full rounded-md
                bg-(--color-surface-alt)
                px-3 py-2
                transition
                hover:bg-(--color-surface)
              "
            >
              <div className="text-sm font-medium text-(--color-text-primary)">
                Export Snapshots
              </div>
              <div className="text-xs text-(--color-text-secondary)">
                Verify and manage sealed audit exports.
              </div>
            </Link>

            <Link
              href="/console/security/audit-keys"
              className="
                block h-full rounded-md
                bg-(--color-surface-alt)
                px-3 py-2
                transition
                hover:bg-(--color-surface)
              "
            >
              <div className="text-sm font-medium text-(--color-text-primary)">
                Signing Keys
              </div>
              <div className="text-xs text-(--color-text-secondary)">
                Access public keys for verification.
              </div>
            </Link>
          </div>
        </Card>
      </div>
    </PageContainer>
  );
}