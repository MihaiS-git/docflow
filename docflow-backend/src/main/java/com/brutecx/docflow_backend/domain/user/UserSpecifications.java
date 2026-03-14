package com.brutecx.docflow_backend.domain.user;

import com.brutecx.docflow_backend.domain.tenant.UserTenantMembership;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class UserSpecifications {

    private UserSpecifications() {}

    /**
     * Filters users by tenant membership.
     */
    public static Specification<User> tenant(UUID tenantId) {
        return (root, query, cb) -> {

            if (tenantId == null) {
                return null;
            }

            // Avoid duplicate users when joining memberships
            assert query != null;
            query.distinct(true);

            var membershipJoin = root.join("memberships", JoinType.INNER);

            return cb.equal(
                    membershipJoin.get("tenant").get("id"),
                    tenantId
            );
        };
    }

    public static Specification<User> status(UserStatus status) {
        return (root, query, cb) ->
                status == null
                        ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<User> emailContains(String email) {
        return (root, query, cb) ->
                (email == null || email.isBlank())
                        ? null
                        : cb.like(
                        cb.lower(root.get("email")),
                        "%" + email.toLowerCase() + "%"
                );
    }
}