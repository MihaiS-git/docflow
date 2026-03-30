package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TenantSpecification {

    public static Specification<Tenant> fromFilter(TenantFilter filter) {
        return allOf(
                hasStatus(filter.status()),
                nameContains(filter.name()),
                regionStartsWith(filter.dataRegion()),
                createdAfter(filter.createdAfter()),
                createdBefore(filter.createdBefore()),
                hasManager(filter.managerName(), filter.managerEmail())
        );
    }

    @SafeVarargs
    private static <T> Specification<T> allOf(Specification<T>... specs) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            for (Specification<T> spec : specs) {
                if (spec == null) continue;

                Predicate p = spec.toPredicate(root, query, cb);
                if (p != null) {
                    predicates.add(p);
                }
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<Tenant> hasStatus(TenantStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<Tenant> nameContains(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) return null;

            return cb.like(
                    cb.lower(root.get("name")),
                    "%" + name.toLowerCase(Locale.ROOT) + "%"
            );
        };
    }

    // ✅ FIXED (prefix match)
    private static Specification<Tenant> regionStartsWith(String region) {
        return (root, query, cb) -> {
            if (region == null || region.isBlank()) return null;

            return cb.like(
                    cb.lower(root.get("dataRegion")),
                    region.toLowerCase(Locale.ROOT) + "%"
            );
        };
    }

    private static Specification<Tenant> createdAfter(java.time.LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) return null;

            Instant from = date
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();

            return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
        };
    }

    private static Specification<Tenant> createdBefore(java.time.LocalDate date) {
        return (root, query, cb) -> {
            if (date == null) return null;

            Instant to = date
                    .plusDays(1)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant();

            return cb.lessThan(root.get("createdAt"), to);
        };
    }

    private static Specification<Tenant> hasManager(String name, String email) {
        return (root, query, cb) -> {

            boolean hasName = name != null && !name.isBlank();
            boolean hasEmail = email != null && !email.isBlank();

            if (!hasName && !hasEmail) return null;

            if (query == null) {
                return null;
            }

            var sub = query.subquery(UUID.class);
            var membership = sub.from(UserTenantMembership.class);
            var user = membership.join("user");

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(membership.get("tenant").get("id"), root.get("id")));
            predicates.add(cb.equal(membership.get("role"), TenantRole.MANAGER));
            predicates.add(cb.equal(membership.get("status"), MembershipStatus.ACTIVE));

            if (hasName) {
                predicates.add(
                        cb.like(
                                cb.lower(
                                        cb.concat(
                                                cb.concat(user.get("firstName"), " "),
                                                user.get("lastName")
                                        )
                                ),
                                "%" + name.toLowerCase(Locale.ROOT) + "%"
                        )
                );
            }

            if (hasEmail) {
                predicates.add(
                        cb.like(
                                cb.lower(user.get("email")),
                                "%" + email.toLowerCase(Locale.ROOT) + "%"
                        )
                );
            }

            sub.select(membership.get("tenant").get("id"))
                    .where(cb.and(predicates.toArray(new Predicate[0])));

            return cb.exists(sub);
        };
    }
}