import type { AuditExportSnapshotDTO } from "./AuditExportSnapshotDTO";

export type AuditExportSnapshotCursorPageDTO = {
  items: AuditExportSnapshotDTO[];
  hasMore: boolean;
  nextCursorCreatedAt: string | null; // ISO Instant
  nextCursorId: string | null; // UUID
};
