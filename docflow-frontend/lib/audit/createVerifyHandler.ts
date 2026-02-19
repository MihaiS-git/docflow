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
  extraParams?: Record<string, string>,
) {
  return async function handleVerify() {
    setVerifying(true);

    try {
      const qs = buildRangeQueryParams({ from, to });

      if (extraParams) {
        for (const [key, value] of Object.entries(extraParams)) {
          if (value) {
            qs.set(key, value);
          }
        }
      }

      const data = await apiFetch<AuditVerificationResultDTO>(
        `${endpoint}/verify?${qs.toString()}`,
      );

      setVerifyResult(data);
    } finally {
      setVerifying(false);
    }
  };
}
