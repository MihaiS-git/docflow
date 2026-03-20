export type AuditExportSnapshotDTO = {
  id: string; // UUID
  stream: string;

  fromTs: string; // ISO Instant
  toTs: string; // ISO Instant

  tenantId: string | null; // UUID or null (global)

  sha256DigestHex: string;
  rowCount: number;

  timestamp: string; // ISO Instant
  createdBy: string; // UUID

  signatureB64: string;
  signatureAlg: string;
  keyId: string | null;
};
