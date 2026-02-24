package com.brutecx.docflow_backend.domain.audit;

import com.brutecx.docflow_backend.audit.credential.CredentialLifecycleAuditEvent;
import com.brutecx.docflow_backend.audit.provenance.AuditResult;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class CredentialLifecycleAuditSpecifications {

    private CredentialLifecycleAuditSpecifications() {
    }

    public static Specification<CredentialLifecycleAuditEvent> timestampFrom(Instant from) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("timestamp"), from);
    }

    public static Specification<CredentialLifecycleAuditEvent> timestampTo(Instant to) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("timestamp"), to);
    }

    public static Specification<CredentialLifecycleAuditEvent> hasCorrelationId(String correlationId) {
        return (root, query, cb) ->
                cb.equal(root.get("correlationId"), correlationId);
    }

    public static Specification<CredentialLifecycleAuditEvent> hasSubjectExternalId(String subjectExternalId) {
        return (root, query, cb) ->
                cb.equal(root.get("subjectExternalId"), subjectExternalId);
    }

    public static Specification<CredentialLifecycleAuditEvent> hasResult(AuditResult result) {
        return (root, query, cb) ->
                cb.equal(root.get("result"), result);
    }

    public static Specification<CredentialLifecycleAuditEvent> cursorAfter(
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
