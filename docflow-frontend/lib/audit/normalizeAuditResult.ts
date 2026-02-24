import type { AuditResult } from "@/types/api/AuditResult";

/**
* Normalizes stream-specific result enums to the canonical UI semantic model.
*
* Rules:
* - SUCCESS -> SUCCESS
* - FAILED / FAILURE -> FAILED
* - DENIED -> DENIED
*
* No metadata inference. Governance-aligned.
*/
export function normalizeAuditResult(result: string): AuditResult {
  switch (result) {
    case "SUCCESS":
      return "SUCCESS";
    case "FAILED":
    case "FAILURE":
      return "FAILED";
    case "DENIED":
      return "DENIED";
    default:
      // Unknown result values should not break rendering; treat as FAILED (conservative).
      return "FAILED";
  }
}
