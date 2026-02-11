package com.brutecx.docflow_backend.domain.user;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> tenant(UUID tenantId) {
        return (root, query, cb) -> {
            if (tenantId == null) return null;

            var tenantJoin = root.join("tenant");
            return cb.equal(tenantJoin.get("id"), tenantId);
        };
    }

    public static Specification<User> status(UserStatus status) {
        return (root, query, cb) ->
                status == null ? null :
                        cb.equal(root.get("status"), status);
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
