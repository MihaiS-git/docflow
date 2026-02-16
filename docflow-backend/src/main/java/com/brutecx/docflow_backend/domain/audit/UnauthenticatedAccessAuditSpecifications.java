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

    public static Specification<UnauthenticatedAccessAuditEvent> cursor(
            Instant ts,
            UUID id,
            SortDirection dir
    ) {
        return (root, q, cb) -> {

            if (dir == SortDirection.ASC) {
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

    public enum SortDirection {
        ASC, DESC
    }
}
