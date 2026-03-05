import { AuditResult } from "./AuditResult";
import { CorrelationSource } from "./CorrelationSource";
import { ExecutionContext } from "./ExecutionContext";

export type AuthenticationEventSource = "SPRING_SECURITY" | "KEYCLOAK_ADMIN_EVENTS";

export type AuthenticationResult = "SUCCESS" | "FAILURE" | "LOGOUT";

export type AuthenticationAuditRow = {
  id: string;
  timestamp: string;

  source: AuthenticationEventSource;

  username: string;
  subjectId: string;

  idp: string;

  authenticationResult: AuthenticationResult;

  ip: string;
  userAgent: string;

  correlationId: string;
  correlationSource: CorrelationSource;
  executionContext: ExecutionContext;

  result: AuditResult;

  eventFingerprint: string;
};
