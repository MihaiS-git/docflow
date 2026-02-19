export type UnauthenticatedAccessAuditRow = {
  id: string;
  timestamp: string;

  correlationId: string | null;
  correlationSource: string | null;
  executionContext: string | null;
  result: string | null;

  httpMethod: string | null;
  path: string | null;
  ip: string | null;
  userAgent: string | null;

  eventFingerprint: string;
  chainVersion: number;
  prevEventHash: string;
  eventHash: string;
};
