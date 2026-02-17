export type AuditVerificationResultDTO = {
  valid: boolean;
  verifiedCount: number;
  failedEventId: string | null;
  failureReason: string | null;
};
