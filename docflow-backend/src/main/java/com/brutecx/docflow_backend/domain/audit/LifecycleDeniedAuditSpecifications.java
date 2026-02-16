package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class LifecycleDeniedAuditSpecifications {

    private LifecycleDeniedAuditSpecifications() {
    }

    public static Specification<LifecycleDeniedAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<LifecycleDeniedAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<LifecycleDeniedAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) -> cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<LifecycleDeniedAuditEvent> hasSubjectId(String subjectId) {
        return (root, query, cb) -> cb.equal(root.get("subjectId"), subjectId);
    }

    /**
     * Cursor predicate for stable pagination using (timestamp, id) as a composite cursor.
     * For DESC (newest first):
     *   - "next page" means strictly older than cursor: (ts < cursorTs) OR (ts == cursorTs AND id < cursorId)
     * For ASC (oldest first):
     *   - "next page" means strictly newer than cursor: (ts > cursorTs) OR (ts == cursorTs AND id > cursorId)
     */
    public static Specification<LifecycleDeniedAuditEvent> cursorAfter(
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
                        cb.and(cb.equal(ts, cursorTs), cb.greaterThan(id, cursorId))
                );
            }

            return cb.or(
                    cb.lessThan(ts, cursorTs),
                    cb.and(cb.equal(ts, cursorTs), cb.lessThan(id, cursorId))
            );
        };
    }
}
