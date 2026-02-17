import { AuthenticationAuditRow } from "./auditApi";

export type AuthenticationAuditCursorPageDTO = {
  items: AuthenticationAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp?: string | null;
  nextCursorId?: string | null;
};
