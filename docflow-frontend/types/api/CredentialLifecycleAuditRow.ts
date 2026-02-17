export type CredentialLifecycleAuditRow = {
  id: string;
  timestamp: string;

  subjectExternalId: string | null;
  clientId: string | null;
  sessionId: string | null;
  ip: string | null;

  eventType: unknown;
  requiredAction: string | null;

  correlationId: string | null;
  correlationSource: unknown;
  executionContext: unknown;

  result: unknown;
  reasonCode: string | null;
  reasonDetail: string | null;

  eventFingerprint: string;

  chainVersion: number;
  prevEventHash: string;
  eventHash: string;
};
