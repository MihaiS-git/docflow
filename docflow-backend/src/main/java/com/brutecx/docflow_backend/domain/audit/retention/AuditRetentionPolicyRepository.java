package com.brutecx.docflow_backend.domain.audit.retention;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuditRetentionPolicyRepository extends JpaRepository<AuditRetentionPolicy, UUID> {
    Optional<AuditRetentionPolicy> findByStreamName(String streamName);
}