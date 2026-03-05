import { AuditResult } from "./AuditResult";
import { CorrelationSource } from "./CorrelationSource";
import { ExecutionContext } from "./ExecutionContext";

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
  correlationSource: CorrelationSource;
  executionContext: ExecutionContext;

  result: AuditResult;

  eventFingerprint: string;

  chainVersion: number;
  prevEventHash: string;
  eventHash: string;
};