"use client";

type Props = {
  verifying: boolean;
  onVerify: () => void;
  result: {
    ok: boolean;
    verifiedCount: number;
    failedCount: number;
    from: string;
    to: string;
    message?: string;
  } | null;
};

export function AuditVerifyPanel({
  verifying,
  onVerify,
  result,
}: Props) {
  return (
    <div className="space-y-3">
      <button
        type="button"
        onClick={onVerify}
        disabled={verifying}
        className="px-3 py-1 rounded border disabled:opacity-50"
      >
        {verifying ? "Verifying…" : "Verify"}
      </button>

      {result && (
        <div className="border rounded p-3 space-y-1 text-sm">
          <div>ok: <span className="font-mono">{String(result.ok)}</span></div>
          <div>verifiedCount: <span className="font-mono">{result.verifiedCount}</span></div>
          <div>failedCount: <span className="font-mono">{result.failedCount}</span></div>
          <div>from: <span className="font-mono">{result.from}</span></div>
          <div>to: <span className="font-mono">{result.to}</span></div>
          {result.message && (
            <div>message: <span className="font-mono">{result.message}</span></div>
          )}
        </div>
      )}
    </div>
  );
}
