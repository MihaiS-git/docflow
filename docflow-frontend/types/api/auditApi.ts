export type AuthenticationAuditRow = {
  id: string;
  timestamp: string;
  source: unknown;
  username: string;
  result: unknown;
  idp: string;
  ip: string;
  userAgent: string;
  correlationId: string;
  correlationSource: unknown;
  executionContext: unknown;
  auditResult: unknown;
  eventFingerprint: string;
};
