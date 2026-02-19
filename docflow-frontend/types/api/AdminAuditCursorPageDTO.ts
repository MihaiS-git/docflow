import { AdminAuditRow } from "./AdminAuditRow";

export type AdminAuditCursorPageDTO = {
  items: AdminAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp: string | null;
  nextCursorId: string | null;
};
