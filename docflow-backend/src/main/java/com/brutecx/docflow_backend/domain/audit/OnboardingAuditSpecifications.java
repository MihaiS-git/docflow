package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.onboarding.OnboardingAuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class OnboardingAuditSpecifications {

    private OnboardingAuditSpecifications() {
    }

    public static Specification<OnboardingAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<OnboardingAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<OnboardingAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) -> cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<OnboardingAuditEvent> hasSubjectId(String subjectId) {
        return (root, query, cb) -> cb.equal(root.get("subjectId"), subjectId);
    }

    public static Specification<OnboardingAuditEvent> hasTenantId(UUID tenantId) {
        return (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<OnboardingAuditEvent> hasInviteId(UUID inviteId) {
        return (root, query, cb) -> cb.equal(root.get("inviteId"), inviteId);
    }

    /**
     * Cursor predicate for stable timeline pagination using (timestamp, id).
     * For DESC (newest first):
     *   - "next page" means strictly older than cursor: (ts < cursorTs) OR (ts == cursorTs AND id < cursorId)
     * For ASC (oldest first):
     *   - "next page" means strictly newer than cursor: (ts > cursorTs) OR (ts == cursorTs AND id > cursorId)
     */
    public static Specification<OnboardingAuditEvent> cursorAfter(Instant cursorTs, UUID cursorId, boolean ascending) {
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
