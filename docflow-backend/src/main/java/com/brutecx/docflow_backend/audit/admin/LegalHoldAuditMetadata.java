package com.brutecx.docflow_backend.audit.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Admin audit metadata for legal hold operations.
 * Compliance-relevant fields:
 * - streamName (target audit stream)
 * - eventId (optional, per-event hold)
 * - correlationId (optional, per-case hold)
 * - reason (human justification)
 */
public record LegalHoldAuditMetadata(
        @JsonProperty("streamName") String streamName,
        @JsonProperty("eventId") String eventId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("caseReferenceId") String caseReferenceId,
        @JsonProperty("reason") String reason
) implements AdminAuditMetadata {
}