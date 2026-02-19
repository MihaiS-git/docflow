import { IdentityProjectionAuditRow } from "./IdentityProjectionAuditRow";

export type IdentityProjectionAuditCursorPageDTO = {
  items: IdentityProjectionAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp: string | null;
  nextCursorId: string | null;
};
