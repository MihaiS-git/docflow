package com.brutecx.docflow_backend.api.dto.audit;

import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import com.brutecx.docflow_backend.audit.provenance.CorrelationSource;
import com.brutecx.docflow_backend.audit.provenance.ExecutionContext;

import java.time.Instant;
import java.util.UUID;

/**
 * GOLD forensic contract for all audit transport DTOs.
 * This interface defines the mandatory fields required
 * for verification, export, and cross-stream tooling.
 * All audit DTO records must implement this interface.
 */
public interface BaseAuditDTO {

    // Identity
    UUID id();
    Instant timestamp();

    // Provenance
    String correlationId();
    CorrelationSource correlationSource();
    ExecutionContext executionContext();
    AuditResult result();

    // Forensic chain
    String eventFingerprint();
    int chainVersion();
    String prevEventHash();
    String eventHash();
}
