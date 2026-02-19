import { UnauthenticatedAccessAuditRow } from "./UnauthenticatedAccessAuditRow";

export type UnauthenticatedAccessAuditCursorPageDTO = {
  items: UnauthenticatedAccessAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp: string | null;
  nextCursorId: string | null;
};
