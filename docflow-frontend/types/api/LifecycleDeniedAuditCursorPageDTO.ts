import { LifecycleDeniedAuditRow } from "./LifecycleDeniedAuditRow";

export type LifecycleDeniedAuditCursorPageDTO = {
  items: LifecycleDeniedAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp?: string | null;
  nextCursorId?: string | null;
};
