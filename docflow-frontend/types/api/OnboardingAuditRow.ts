export type OnboardingAuditRow = {
  id: string;
  timestamp: string;
  actorUserId: string | null;
  subjectId: string | null;
  tenantId: string | null;
  inviteId: string | null;
  correlationId: string | null;
  correlationSource: string | null;
  executionContext: string | null;
  ip: string | null;
  userAgent: string | null;
  result: string | null;
  outcome: string | null;
  reasonCode: string | null;
  reasonDetail: string | null;
  eventFingerprint: string;
};
