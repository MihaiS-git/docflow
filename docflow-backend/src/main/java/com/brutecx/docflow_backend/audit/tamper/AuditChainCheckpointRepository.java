package com.brutecx.docflow_backend.audit.tamper;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuditChainCheckpointRepository
        extends JpaRepository<AuditChainCheckpoint, String> {

    Optional<AuditChainCheckpoint> findByStream(String stream);
}