import { CredentialLifecycleAuditRow } from "./CredentialLifecycleAuditRow";

export type CredentialLifecycleAuditCursorPageDTO = {
  items: CredentialLifecycleAuditRow[];
  hasMore: boolean;
  nextCursorTimestamp?: string | null;
  nextCursorId?: string | null;
};
