package com.brutecx.docflow_backend.domain.audit.retention;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "audit_retention_policies",
        indexes = {
                @Index(name = "idx_audit_retention_policy_stream", columnList = "stream_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_audit_retention_policy_stream", columnNames = "stream_name")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditRetentionPolicy {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "stream_name", nullable = false, updatable = false, length = 128)
    private String streamName;

    @Column(name = "retention_days", nullable = false)
    @Positive
    private int retentionDays;

    @Column(name = "archive_enabled", nullable = false)
    private boolean archiveEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AuditRetentionPolicy(
            String streamName,
            int retentionDays,
            boolean archiveEnabled
    ) {
        this.streamName = requireNonBlank(streamName, "streamName");
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be > 0");
        }
        this.retentionDays = retentionDays;
        this.archiveEnabled = archiveEnabled;

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(int retentionDays, boolean archiveEnabled) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be > 0");
        }
        this.retentionDays = retentionDays;
        this.archiveEnabled = archiveEnabled;
        this.updatedAt = Instant.now();
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return v.trim();
    }
}