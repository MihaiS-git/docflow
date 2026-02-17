import { apiFetch } from "@/lib/apiFetch";
import { buildRangeQueryParams } from "@/lib/audit/auditRange";
import { AuditVerificationResultDTO } from "@/lib/api/AuditVerificationResultDTO";

export function createVerifyHandler(
  endpoint: string,
  from: string,
  to: string,
  setVerifyResult: (
    value: AuditVerificationResultDTO | null,
  ) => void,
  setVerifying: (value: boolean) => void,
) {
  return async function handleVerify() {
    setVerifying(true);

    try {
      const qs = buildRangeQueryParams({ from, to });

      const data = await apiFetch<AuditVerificationResultDTO>(
        `${endpoint}/verify?${qs.toString()}`,
      );

      setVerifyResult(data);
    } finally {
      setVerifying(false);
    }
  };
}
