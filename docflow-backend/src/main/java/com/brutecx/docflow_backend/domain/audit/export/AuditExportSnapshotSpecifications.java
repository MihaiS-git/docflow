package com.brutecx.docflow_backend.domain.audit.export;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class AuditExportSnapshotSpecifications {

    private AuditExportSnapshotSpecifications() {}

    public static Specification<AuditExportSnapshot> hasStream(String stream) {
        return (root, q, cb) -> cb.equal(root.get("stream"), stream);
    }

    public static Specification<AuditExportSnapshot> hasTenantId(UUID tenantId) {
        return (root, q, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<AuditExportSnapshot> hasCreatedBy(UUID createdBy) {
        return (root, q, cb) -> cb.equal(root.get("createdBy"), createdBy);
    }

    public static Specification<AuditExportSnapshot> createdFrom(Instant from) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditExportSnapshot> createdTo(Instant to) {
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<AuditExportSnapshot> exportRangeFrom(Instant fromTs) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("fromTs"), fromTs);
    }

    public static Specification<AuditExportSnapshot> exportRangeTo(Instant toTs) {
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("toTs"), toTs);
    }

    /**
     * Cursor pagination for DESC ordering:
     * Order: createdAt DESC, id DESC
     * For next page after cursor (createdAt,id):
     *  return items where:
     *    createdAt < cursorCreatedAt
     *    OR (createdAt == cursorCreatedAt AND id < cursorId)
     */
    public static Specification<AuditExportSnapshot> cursorAfterDesc(Instant cursorCreatedAt, UUID cursorId) {
        return (root, q, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), cursorCreatedAt),
                cb.and(
                        cb.equal(root.get("createdAt"), cursorCreatedAt),
                        cb.lessThan(root.get("id"), cursorId)
                )
        );
    }
}
