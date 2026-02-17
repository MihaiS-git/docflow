export type RbacDeniedAuditRow = {
  id: string;
  timestamp: string;
  correlationId: string | null;
  correlationSource: unknown;
  executionContext: unknown;
  result: unknown;
  subjectId: string;
  httpMethod: string;
  path: string;
  ip: string;
  userAgent: string;
  eventFingerprint: string;
};
