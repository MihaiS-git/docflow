export type SensitiveAccessAuditRow = {
  timestamp: string;

  actorUserId: string | null;
  actorExternalSubjectId: string | null;
  tenantId: string | null;

  subjectType: string | null;
  subjectId: string | null;
  resource: string | null;
  action: string | null;
  resourcePath: string | null;

  correlationId: string | null;
  correlationSource: string | null;
  executionContext: string | null;
  result: string | null;
  ip: string | null;
  userAgent: string | null;

  reasonCode: string | null;
  reasonDetail: string | null;
  dataClassification: string | null;

  eventFingerprint: string;
};
