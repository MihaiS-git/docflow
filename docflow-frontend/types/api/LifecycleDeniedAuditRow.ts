export type LifecycleDeniedAuditRow = {
  id: string;
  timestamp: string;
  subjectId: string | null;
  reasonCode: string | null;
  httpMethod: string | null;
  path: string | null;
  ip: string | null;
  userAgent: string | null;
  correlationId: string | null;
  eventFingerprint: string;
};
