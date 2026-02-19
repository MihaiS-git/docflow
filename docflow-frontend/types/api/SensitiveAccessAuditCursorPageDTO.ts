import { SensitiveAccessAuditRow } from "./SensitiveAccessAuditRow";

export type SensitiveAccessAuditCursorPageDTO = {
  items: SensitiveAccessAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp: string | null;
  nextCursorId: string | null;
};
