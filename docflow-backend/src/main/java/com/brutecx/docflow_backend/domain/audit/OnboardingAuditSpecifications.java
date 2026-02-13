package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class OnboardingAuditSpecifications {

    private OnboardingAuditSpecifications() {}

    public static Specification<OnboardingAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<OnboardingAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<OnboardingAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<OnboardingAuditEvent> hasSubjectId(String subjectId) {
        return (root, query, cb) ->
                cb.equal(root.get("subjectId"), subjectId);
    }

    public static Specification<OnboardingAuditEvent> hasTenantId(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<OnboardingAuditEvent> cursorAfter(
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
