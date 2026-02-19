import { AuditResult } from "./AuditResult";
import { CorrelationSource } from "./CorrelationSource";
import { ExecutionContext } from "./ExecutionContext";

export type CredentialLifecycleEventType =
  | "PASSWORD_CHANGED"
  | "PASSWORD_RESET"
  | "MFA_ENROLLED"
  | "MFA_REMOVED"
  | "REQUIRED_ACTION_SET"
  | "REQUIRED_ACTION_CLEARED"
  | "UNKNOWN";

export type CredentialLifecycleAuditRow = {
  id: string;
  timestamp: string;
  subjectExternalId: string | null;
  clientId: string | null;
  sessionId: string | null;
  ip: string | null;
  eventType: CredentialLifecycleEventType;
  requiredAction: string | null;
  correlationId: string | null;
  correlationSource: CorrelationSource;
  executionContext: ExecutionContext;
  result: AuditResult;
  reasonCode: string | null;
  reasonDetail: string | null;
  eventFingerprint: string;
  chainVersion: number;
  prevEventHash: string;
  eventHash: string;
};
