"use client";

import Button from "@/components/ui/Button";
import { AuditVerificationResultDTO } from "../api/AuditVerificationResultDTO";

type Props = {
  verifying: boolean;
  onVerify: () => void;
  result: AuditVerificationResultDTO | null;
  from: string;
  to: string;
};

export function AuditVerifyPanel({
  verifying,
  onVerify,
  result,
  from,
  to,
}: Props) {
  return (
    <div className="space-y-3">
      <Button
        variant="outline"
        onClick={onVerify}
        disabled={verifying}
      >
        {verifying ? "Verifying…" : "Verify"}
      </Button>

      {result && (
        <div className="border rounded p-3 space-y-1 text-sm">
          <div>
            valid:{" "}
            <span className="font-mono">{String(result.valid)}</span>
          </div>

          <div>
            verifiedCount:{" "}
            <span className="font-mono">{result.verifiedCount}</span>
          </div>

          <div>
            failedEventId:{" "}
            <span className="font-mono">
              {result.failedEventId ?? "-"}
            </span>
          </div>

          <div>
            failureReason:{" "}
            <span className="font-mono">
              {result.failureReason ?? "-"}
            </span>
          </div>

          <div>
            from: <span className="font-mono">{from}</span>
          </div>

          <div>
            to: <span className="font-mono">{to}</span>
          </div>
        </div>
      )}
    </div>
  );
}
