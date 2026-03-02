package com.brutecx.docflow_backend.audit.keyrotation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "audit_export_signing_key_rotation_events",
        indexes = {
                @Index(name = "idx_audit_key_rot_ts_id", columnList = "timestamp,id"),
                @Index(name = "idx_audit_key_rot_corr", columnList = "correlation_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditExportSigningKeyRotationEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(nullable = false, updatable = false, name = "correlation_id")
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
    private AuditExportSigningKeyRotationMetadata metadata;

    @Column(nullable = false, updatable = false, unique = true, name = "event_fingerprint")
    private String eventFingerprint;

    @Column(nullable = false, updatable = false, name = "chain_version")
    private int chainVersion;

    @Column(nullable = false, updatable = false, name = "prev_event_hash")
    private String prevEventHash;

    @Column(nullable = false, updatable = false, name = "event_hash")
    private String eventHash;

    public AuditExportSigningKeyRotationEvent(
            Instant timestamp,
            String correlationId,
            AuditExportSigningKeyRotationMetadata metadata,
            String eventFingerprint,
            int chainVersion,
            String prevEventHash,
            String eventHash
    ) {
        this.timestamp = Objects.requireNonNull(timestamp);
        this.correlationId = requireNonBlank(correlationId);
        this.metadata = Objects.requireNonNull(metadata);
        this.eventFingerprint = requireNonBlank(eventFingerprint);
        this.prevEventHash = requireNonBlank(prevEventHash);
        this.eventHash = requireNonBlank(eventHash);

        if (chainVersion <= 0) throw new IllegalArgumentException("chainVersion must be > 0");
        this.chainVersion = chainVersion;
    }

    private static String requireNonBlank(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Field must not be blank");
        return v;
    }

}