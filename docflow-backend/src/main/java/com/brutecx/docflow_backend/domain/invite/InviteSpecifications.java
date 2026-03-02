package com.brutecx.docflow_backend.domain.invite;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class InviteSpecifications {

    private InviteSpecifications() {}

    public static Specification<Invite> byTenant(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<Invite> emailStartsWith(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("email")),
                        email.toLowerCase() + "%"
                );
    }

    public static Specification<Invite> hasStatus(InviteStatus status) {
        if (status == null) {
            return null;
        }

        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }
}