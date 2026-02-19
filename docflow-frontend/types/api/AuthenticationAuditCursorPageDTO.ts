import { AuthenticationAuditRow } from "./AuthenticationAuditRow";

export type AuthenticationAuditCursorPageDTO = {
  items: AuthenticationAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp?: string | null;
  nextCursorId?: string | null;
};
