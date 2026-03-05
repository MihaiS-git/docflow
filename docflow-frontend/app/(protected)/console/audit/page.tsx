"use client";

import { AUDIT_STREAMS } from "@/lib/audit/auditStreams";
import { useAuth } from "@/lib/auth/useAuth";
import Link from "next/link";
import { useState } from "react";

export default function AuditConsolePage() {
  const { identity } = useAuth();
  const [query, setQuery] = useState("");

  if (!identity?.roles.includes("AUDITOR")) return null;
/* 
  const streams = AUDIT_STREAMS.filter((s) =>
    s.name.toLowerCase().includes(query.toLowerCase()),
  ); */

  const grouped = AUDIT_STREAMS.reduce<Record<string, typeof AUDIT_STREAMS>>(
    (acc, stream) => {
      acc[stream.category] ??= [];
      acc[stream.category].push(stream);
      return acc;
    },
    {},
  );

  return (
    <div className="p-6 space-y-10">
      <header>
        <h1 className="text-2xl font-semibold">Audit Console</h1>
        <p className="text-sm text-gray-500 mt-1">
          Browse system audit streams and validate forensic export artifacts.
        </p>
      </header>

      <input
        type="text"
        placeholder="Search audit streams..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="w-full max-w-sm border rounded-md px-3 py-2 text-sm"
      />

      {/* Audit Streams */}
      {Object.entries(grouped).map(([category, streams]) => (
        <section key={category} className="space-y-4">
          <h3 className="text-md font-semibold text-gray-700">{category}</h3>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {streams.map((stream) => (
              <Link
                key={stream.path}
                href={`/console/audit/streams/${stream.path}`}
                className="border rounded-lg p-4 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition"
              >
                <div className="font-semibold">{stream.name}</div>
                <div className="text-sm text-gray-500">
                  Inspect events recorded in the {stream.name} audit stream.
                </div>
              </Link>
            ))}
          </div>
        </section>
      ))}

      {/* Audit Governance */}
      <section className="space-y-4 border rounded-xl p-6 bg-zinc-50 dark:bg-zinc-900">
        <h2 className="text-lg font-semibold">Audit Integrity & Governance</h2>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <Link
            href="/console/audit/retention"
            className="border rounded-lg p-4 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition"
          >
            <div className="font-semibold">Retention Policies</div>
            <div className="text-sm text-gray-500">
              Configure retention periods and cleanup policies for audit data.
            </div>
          </Link>

          <Link
            href="/console/audit/exports"
            className="border rounded-lg p-4 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition"
          >
            <div className="font-semibold">Audit Export Snapshots</div>
            <div className="text-sm text-gray-500">
              Registry and verification of sealed JSONL forensic exports.
            </div>
          </Link>

          <Link
            href="/console/security/audit-keys"
            className="border rounded-lg p-4 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition border-red-200"
          >
            <div className="font-semibold">Export Signing Keys</div>
            <div className="text-sm text-gray-500">
              Download public keys used to verify signed JSONL audit export
              snapshots.
            </div>
          </Link>
        </div>
      </section>
    </div>
  );
}
