export type RbacDeniedAuditRow = {
  id: string;
  timestamp: string;
  subjectId: string;
  httpMethod: string;
  path: string;
  ip: string;
  userAgent: string;
  correlationId: string | null;
  eventFingerprint: string;

  correlationSource: unknown;
  executionContext: unknown;
  result: unknown;
};
