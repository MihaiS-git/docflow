import { RbacDeniedAuditRow } from "./RbacDeniedAuditRow";

export type RbacDeniedAuditCursorPageDTO = {
  items: RbacDeniedAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp?: string | null;
  nextCursorId?: string | null;
};
