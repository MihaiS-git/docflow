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
     * Cursor predicate for stable timeline pagination using (timestamp, id).
     *
     * For DESC (newest first):
     *   - "next page" means strictly older than cursor:
     *     (ts < cursorTs) OR (ts == cursorTs AND id < cursorId)
     *
     * For ASC (oldest first):
     *   - "next page" means strictly newer than cursor:
     *     (ts > cursorTs) OR (ts == cursorTs AND id > cursorId)
     */
    public static Specification<SensitiveAccessAuditEvent> cursorAfter(
            Instant cursorTs,
            UUID cursorId,
            boolean ascending
    ) {
        return (root, query, cb) -> {

            var ts = root.get("timestamp").as(Instant.class);
            var id = root.get("id").as(UUID.class);

            if (ascending) {
                return cb.or(
                        cb.greaterThan(ts, cursorTs),
                        cb.and(
                                cb.equal(ts, cursorTs),
                                cb.greaterThan(id, cursorId)
                        )
                );
            }

            return cb.or(
                    cb.lessThan(ts, cursorTs),
                    cb.and(
                            cb.equal(ts, cursorTs),
                            cb.lessThan(id, cursorId)
                    )
            );
        };
    }
}
