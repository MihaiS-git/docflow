package com.brutecx.docflow_backend.domain.tenant;

import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class UserTenantMembershipSpecification {

    private UserTenantMembershipSpecification() {}

    public static Specification<UserTenantMembership> byTenant(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenant").get("id"), tenantId);
    }

    public static Specification<UserTenantMembership> hasRole(TenantRole role) {
        return (root, query, cb) ->
                role == null ? null : cb.equal(root.get("role"), role);
    }

    public static Specification<UserTenantMembership> hasStatus(MembershipStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<UserTenantMembership> search(String search) {
        return (root, query, cb) -> {
            if (search == null) return null;

            Join<Object, Object> user = root.join("user");

            String like = "%" + search.toLowerCase(Locale.ROOT) + "%";

            return cb.or(
                    cb.like(cb.lower(user.get("displayName")), like),
                    cb.like(cb.lower(user.get("email")), like)
            );
        };
    }

    public static Specification<UserTenantMembership> jobTitle(String jobTitle) {
        return (root, query, cb) -> {
            if (jobTitle == null) return null;

            Join<Object, Object> user = root.join("user");
            return cb.like(cb.lower(user.get("jobTitle")), jobTitle);
        };
    }

    public static Specification<UserTenantMembership> department(String department) {
        return (root, query, cb) -> {
            if (department == null) return null;

            Join<Object, Object> user = root.join("user");
            return cb.like(cb.lower(user.get("department")), department);
        };
    }

    public static Specification<UserTenantMembership> createdAfter(Instant after) {
        return (root, query, cb) ->
                after == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), after);
    }

    public static Specification<UserTenantMembership> createdBefore(Instant before) {
        return (root, query, cb) ->
                before == null ? null : cb.lessThan(root.get("createdAt"), before);
    }

    public static Specification<UserTenantMembership> fetchUser() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("user");
                query.distinct(true);
            }
            return null;
        };
    }
}