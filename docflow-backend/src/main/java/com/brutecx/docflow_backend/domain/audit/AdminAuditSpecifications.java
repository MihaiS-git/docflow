package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.admin.AdminAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class AdminAuditSpecifications {

    private AdminAuditSpecifications() {
    }

    public static Specification<AdminAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<AdminAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<AdminAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<AdminAuditEvent> hasActorUserId(UUID actorUserId) {
        return (root, query, cb) ->
                cb.equal(root.get("actorUserId"), actorUserId);
    }

    public static Specification<AdminAuditEvent> hasTenantId(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<AdminAuditEvent> cursorAfter(
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
            } else {
                return cb.or(
                        cb.lessThan(root.get("timestamp"), cursorTimestamp),
                        cb.and(
                                cb.equal(root.get("timestamp"), cursorTimestamp),
                                cb.lessThan(root.get("id"), cursorId)
                        )
                );
            }
        };
    }
}
