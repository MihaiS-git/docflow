package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.sensitive.SensitiveAccessAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class SensitiveAccessAuditSpecifications {

    private SensitiveAccessAuditSpecifications() {
    }

    public static Specification<SensitiveAccessAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<SensitiveAccessAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<SensitiveAccessAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<SensitiveAccessAuditEvent> hasSubjectId(String subjectId) {
        return (root, query, cb) ->
                cb.equal(root.get("subjectId"), subjectId);
    }

    public static Specification<SensitiveAccessAuditEvent> hasTenantId(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<SensitiveAccessAuditEvent> hasActorUserId(UUID actorUserId) {
        return (root, query, cb) ->
                cb.equal(root.get("actorUserId"), actorUserId);
    }

    /**
     * Cursor predicate for deterministic ordering by (timestamp, id).
     * <p>
     * For ASC:
     * - fetch rows AFTER (cursorTimestamp, cursorId)
     * <p>
     * For DESC:
     * - fetch rows BEFORE (cursorTimestamp, cursorId)
     */
    public static Specification<SensitiveAccessAuditEvent> cursor(
            Instant cursorTimestamp,
            UUID cursorId,
            SortDirection direction
    ) {
        return (root, query, cb) -> {

            if (direction == SortDirection.ASC) {
                return cb.or(
                        cb.greaterThan(root.get("timestamp"), cursorTimestamp),
                        cb.and(
                                cb.equal(root.get("timestamp"), cursorTimestamp),
                                cb.greaterThan(root.get("id"), cursorId)
                        )
                );
            }

            return cb.or(
                    cb.lessThan(root.get("timestamp"), cursorTimestamp),
                    cb.and(
                            cb.equal(root.get("timestamp"), cursorTimestamp),
                            cb.lessThan(root.get("id"), cursorId)
                    )
            );
        };
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}
