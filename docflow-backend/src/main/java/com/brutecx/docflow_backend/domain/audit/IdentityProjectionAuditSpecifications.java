package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.identity.IdentityProjectionAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class IdentityProjectionAuditSpecifications {

    private IdentityProjectionAuditSpecifications() {}

    public static Specification<IdentityProjectionAuditEvent> timestampFrom(Instant from) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<IdentityProjectionAuditEvent> timestampTo(Instant to) {
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<IdentityProjectionAuditEvent> hasSubjectId(String subjectId) {
        return (root, q, cb) -> cb.equal(root.get("subjectId"), subjectId);
    }

    public static Specification<IdentityProjectionAuditEvent> hasCorrelationId(String correlationId) {
        return (root, q, cb) -> cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<IdentityProjectionAuditEvent> cursorAfter(
            Instant ts,
            UUID id,
            boolean asc
    ) {
        return (root, q, cb) -> {
            if (asc) {
                return cb.or(
                        cb.greaterThan(root.get("timestamp"), ts),
                        cb.and(
                                cb.equal(root.get("timestamp"), ts),
                                cb.greaterThan(root.get("id"), id)
                        )
                );
            }
            return cb.or(
                    cb.lessThan(root.get("timestamp"), ts),
                    cb.and(
                            cb.equal(root.get("timestamp"), ts),
                            cb.lessThan(root.get("id"), id)
                    )
            );
        };
    }
}
