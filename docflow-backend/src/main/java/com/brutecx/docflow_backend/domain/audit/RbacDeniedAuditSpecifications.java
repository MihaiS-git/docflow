package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.rbac.RbacDeniedAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class RbacDeniedAuditSpecifications {

    private RbacDeniedAuditSpecifications() {
    }

    public static Specification<RbacDeniedAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<RbacDeniedAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<RbacDeniedAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) -> cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<RbacDeniedAuditEvent> hasSubjectId(String subjectId) {
        return (root, query, cb) -> cb.equal(root.get("subjectId"), subjectId);
    }

    /**
     * Cursor filter for deterministic ordering (timestamp + id).
     * When ascending=true -> fetch rows strictly after (ts,id).
     * When ascending=false -> fetch rows strictly before (ts,id).
     */
    public static Specification<RbacDeniedAuditEvent> cursorAfter(
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
