package com.brutecx.docflow_backend.domain.audit.export;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "audit_export_snapshot",
        indexes = {
                @Index(name = "idx_audit_export_snapshot_stream", columnList = "stream"),
                @Index(name = "idx_audit_export_snapshot_tenant", columnList = "tenantId"),
                @Index(name = "idx_audit_export_snapshot_timestamp", columnList = "timestamp"),
                @Index(name = "idx_audit_export_snapshot_created_by", columnList = "createdBy")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA only
public class AuditExportSnapshot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 128, updatable = false)
    private String stream;

    @Column(nullable = false, updatable = false, name="from_ts")
    private Instant fromTs;

    @Column(nullable = false, updatable = false, name="to_ts")
    private Instant toTs;

    @Column(updatable = false, name="tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 64, updatable = false, name="sha256_digest_hex")
    private String sha256DigestHex;

    @Column(nullable = false, updatable = false, name="row_count")
    private long rowCount;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, name="created_by")
    private UUID createdBy;

    @Column(nullable = false, length = 1024, updatable = false, name="signature_b64")
    private String signatureB64;

    @Column(nullable = false, length = 64, updatable = false, name="signature_alg")
    private String signatureAlg;

    @Column(length = 128, updatable = false, name="key_id")
    private String keyId;

    public AuditExportSnapshot(
            String stream,
            Instant fromTs,
            Instant toTs,
            UUID tenantId,
            String sha256DigestHex,
            long rowCount,
            Instant timestamp,
            UUID createdBy,
            String signatureB64,
            String signatureAlg,
            String keyId
    ) {
        this.stream = stream;
        this.fromTs = fromTs;
        this.toTs = toTs;
        this.tenantId = tenantId;
        this.sha256DigestHex = sha256DigestHex;
        this.rowCount = rowCount;
        this.timestamp = timestamp;
        this.createdBy = createdBy;
        this.signatureB64 = signatureB64;
        this.signatureAlg = signatureAlg;
        this.keyId = keyId;
    }
}
