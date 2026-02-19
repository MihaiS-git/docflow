export type IdentityProjectionAuditRow = {
  id: string;
  timestamp: string;
  subjectId: string;
  correlationId: string | null;
  executionContext: string | null;
  correlationSource: string | null;
  result: string | null;
  reasonCode: string | null;
  eventFingerprint: string;
  chainVersion: number;
  prevHash: string;
  eventHash: string;
};
