package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.lifecycle.LifecycleDeniedAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class LifecycleDeniedAuditSpecifications {

    private LifecycleDeniedAuditSpecifications() {}

    public static Specification<LifecycleDeniedAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<LifecycleDeniedAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<LifecycleDeniedAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<LifecycleDeniedAuditEvent> hasSubjectId(String subjectId) {
        return (root, query, cb) ->
                cb.equal(root.get("subjectId"), subjectId);
    }

    public static Specification<LifecycleDeniedAuditEvent> cursorAfter(
            Instant cursorTimestamp,
            UUID cursorId,
            boolean ascending
    ) {
        return (root, query, cb) -> {
            if (ascending) {
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
}
