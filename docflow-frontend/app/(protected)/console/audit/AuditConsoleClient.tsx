"use client";

import { useState } from "react";
import { apiFetch } from "@/lib/apiFetch";
import type { SpringPage } from "@/types/api/SpringPage";

type AuthenticationAuditRow = {
  id: string;
  timestamp: string;
  source: string;
  username: string;
  result: string;
  idp: string;
  ip: string;
  userAgent: string;
  correlationId: string;
  correlationSource: string;
  executionContext: string;
  auditResult: string;
  eventFingerprint: string;
};

export default function AuditConsoleClient() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [limit, setLimit] = useState(20);

  const [rows, setRows] = useState<AuthenticationAuditRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [queried, setQueried] = useState(false);

  const canQuery = Boolean(from && to && !loading);

  async function handleQuery() {
    if (!canQuery) return;

    setLoading(true);
    setError(null);

    try {
      const qs = new URLSearchParams();

      qs.set("from", new Date(from).toISOString());
      qs.set("to", new Date(to).toISOString());
      qs.set("size", String(limit));
      qs.set("direction", "DESC");

      const data = await apiFetch<SpringPage<AuthenticationAuditRow>>(
        `/api/audit/authentication?${qs.toString()}`
      );

      setRows(data.content);
      setQueried(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Query failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-semibold">
        Admin Audit Console — Authentication
      </h1>

      <div className="border rounded p-3 space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <div className="space-y-1">
            <label className="text-sm font-medium">From</label>
            <input
              type="datetime-local"
              className="border rounded px-2 py-1 w-full"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium">To</label>
            <input
              type="datetime-local"
              className="border rounded px-2 py-1 w-full"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium">Limit</label>
            <input
              type="number"
              min={1}
              max={100}
              className="border rounded px-2 py-1 w-full"
              value={limit}
              onChange={(e) => setLimit(Number(e.target.value))}
            />
          </div>
        </div>

        <button
          type="button"
          onClick={handleQuery}
          disabled={!canQuery}
          className="px-3 py-1 rounded border disabled:opacity-50"
        >
          Query
        </button>
      </div>

      {!queried && (
        <div className="text-sm text-gray-600">
          Select a date range and click Query to load audit events.
        </div>
      )}

      {error && (
        <div className="text-sm text-red-600">
          {error}
        </div>
      )}

      {queried && rows.length === 0 && !error && (
        <div className="text-sm text-gray-600">
          No results found for selected range.
        </div>
      )}

      {rows.length > 0 && (
        <div className="overflow-auto border rounded">
          <table className="min-w-full text-xs">
            <thead>
              <tr>
                <th className="p-2 border-b">timestamp</th>
                <th className="p-2 border-b">id</th>
                <th className="p-2 border-b">source</th>
                <th className="p-2 border-b">username</th>
                <th className="p-2 border-b">result</th>
                <th className="p-2 border-b">idp</th>
                <th className="p-2 border-b">ip</th>
                <th className="p-2 border-b">userAgent</th>
                <th className="p-2 border-b">correlationId</th>
                <th className="p-2 border-b">correlationSource</th>
                <th className="p-2 border-b">executionContext</th>
                <th className="p-2 border-b">auditResult</th>
                <th className="p-2 border-b">eventFingerprint</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="p-2 whitespace-nowrap">{r.timestamp}</td>
                  <td className="p-2 whitespace-nowrap">{r.id}</td>
                  <td className="p-2 whitespace-nowrap">{r.source}</td>
                  <td className="p-2 whitespace-nowrap">{r.username}</td>
                  <td className="p-2 whitespace-nowrap">{r.result}</td>
                  <td className="p-2 whitespace-nowrap">{r.idp}</td>
                  <td className="p-2 whitespace-nowrap">{r.ip}</td>
                  <td className="p-2 whitespace-nowrap">{r.userAgent}</td>
                  <td className="p-2 whitespace-nowrap">{r.correlationId}</td>
                  <td className="p-2 whitespace-nowrap">{r.correlationSource}</td>
                  <td className="p-2 whitespace-nowrap">{r.executionContext}</td>
                  <td className="p-2 whitespace-nowrap">{r.auditResult}</td>
                  <td className="p-2 whitespace-nowrap font-mono">
                    {r.eventFingerprint}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
