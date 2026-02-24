package com.brutecx.docflow_backend.domain.audit.retention;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

public final class AuditLegalHoldSpecifications {

    private AuditLegalHoldSpecifications() {}

    public static Specification<AuditLegalHold> streamNameEqualsIgnoreCase(String streamName) {
        if (streamName == null || streamName.isBlank()) {
            return null;
        }
        String normalized = streamName.trim().toUpperCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.upper(root.get("streamName")), normalized);
    }

    public static Specification<AuditLegalHold> activeEquals(Boolean active) {
        if (active == null) return null;
        return (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    public static Specification<AuditLegalHold> caseReferenceIdEquals(String caseReferenceId) {
        if (caseReferenceId == null || caseReferenceId.isBlank()) return null;
        String normalized = caseReferenceId.trim();
        return (root, query, cb) -> cb.equal(root.get("caseReferenceId"), normalized);
    }

    public static Specification<AuditLegalHold> correlationIdEquals(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) return null;
        String normalized = correlationId.trim();
        return (root, query, cb) -> cb.equal(root.get("correlationId"), normalized);
    }

    public static Specification<AuditLegalHold> eventIdEquals(UUID eventId) {
        if (eventId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("eventId"), eventId);
    }

    public static Specification<AuditLegalHold> createdByEquals(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) return null;
        String normalized = createdBy.trim();
        return (root, query, cb) -> cb.equal(root.get("createdBy"), normalized);
    }
}