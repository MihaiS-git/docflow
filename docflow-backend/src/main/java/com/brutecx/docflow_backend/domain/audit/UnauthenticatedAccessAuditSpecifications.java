package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.unauth.UnauthenticatedAccessAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class UnauthenticatedAccessAuditSpecifications {

    private UnauthenticatedAccessAuditSpecifications() {}

    public static Specification<UnauthenticatedAccessAuditEvent> timestampFrom(Instant from) {
        return (root, q, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<UnauthenticatedAccessAuditEvent> timestampTo(Instant to) {
        return (root, q, cb) ->
                cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<UnauthenticatedAccessAuditEvent> hasCorrelationId(String correlationId) {
        return (root, q, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    /**
     * Cursor predicate for stable pagination using (timestamp, id).
     * For DESC (newest first):
     *   next page = strictly older than cursor:
     *     (ts < cursorTs) OR (ts == cursorTs AND id < cursorId)
     * For ASC (oldest first):
     *   next page = strictly newer than cursor:
     *     (ts > cursorTs) OR (ts == cursorTs AND id > cursorId)
     */
    public static Specification<UnauthenticatedAccessAuditEvent> cursorAfter(
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
