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
                @Index(name = "idx_audit_export_snapshot_created_at", columnList = "createdAt"),
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

    @Column(nullable = false, updatable = false)
    private Instant fromTs;

    @Column(nullable = false, updatable = false)
    private Instant toTs;

    @Column(updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256DigestHex;

    @Column(nullable = false, updatable = false)
    private long rowCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 1024, updatable = false)
    private String signatureB64;

    @Column(nullable = false, length = 64, updatable = false)
    private String signatureAlg;

    @Column(length = 128, updatable = false)
    private String keyId;

    public AuditExportSnapshot(
            String stream,
            Instant fromTs,
            Instant toTs,
            UUID tenantId,
            String sha256DigestHex,
            long rowCount,
            Instant createdAt,
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
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.signatureB64 = signatureB64;
        this.signatureAlg = signatureAlg;
        this.keyId = keyId;
    }
}
