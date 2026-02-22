export type AuditExportVerificationResultDTO = {
  ok: boolean;

  snapshotId: string; // UUID
  stream: string | null;
  tenantId: string | null;

  exportFrom: string | null; // ISO Instant
  exportTo: string | null;

  expectedRowCount: number;

  expectedPayloadSha256Hex: string | null;
  computedPayloadSha256Hex: string | null;

  digestMatches: boolean;

  keyIdExists: boolean;
  signatureValid: boolean;

  signatureAlgorithm: string | null;
  keyId: string | null;

  metaPresent: boolean;
  metaParsed: boolean;
  metaSnapshotIdMatches: boolean;
  metaDigestMatches: boolean;
  metaSignatureMatches: boolean;
  metaKeyIdMatches: boolean;
  metaAlgorithmMatches: boolean;
  metaDigestAlgorithmMatches: boolean;
  metaSignatureInputMatches: boolean;
  metaStreamMatches: boolean;
  metaRangeMatches: boolean;
  metaTenantMatches: boolean;
  metaRowCountMatches: boolean;

  message: string;
};
