package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantFilter;
import com.brutecx.docflow_backend.domain.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TenantSpecification {

    public static Specification<Tenant> fromFilter(TenantFilter filter) {

        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            boolean hasManagerName =
                    filter.managerName() != null && !filter.managerName().isBlank();

            boolean hasManagerEmail =
                    filter.managerEmail() != null && !filter.managerEmail().isBlank();

            boolean hasManagerFilter = hasManagerName || hasManagerEmail;

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            if (filter.name() != null && !filter.name().isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("name")),
                                "%" + filter.name().toLowerCase(Locale.ROOT) + "%"
                        )
                );
            }

            if (filter.dataRegion() != null && !filter.dataRegion().isBlank()) {
                predicates.add(cb.equal(root.get("dataRegion"), filter.dataRegion()));
            }

            if (filter.createdAfter() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                filter.createdAfter()
                                        .atStartOfDay()
                                        .toInstant(ZoneOffset.UTC)
                        )
                );
            }

            if (filter.createdBefore() != null) {
                predicates.add(
                        cb.lessThan(
                                root.get("createdAt"),
                                filter.createdBefore()
                                        .plusDays(1)
                                        .atStartOfDay()
                                        .toInstant(ZoneOffset.UTC)
                        )
                );
            }

            if (hasManagerFilter) {

                if (query != null) {
                    query.distinct(true);
                }

                Join<Tenant, UserTenantMembership> membership =
                        root.join("memberships", JoinType.INNER);

                Join<UserTenantMembership, User> user =
                        membership.join("user", JoinType.INNER);

                predicates.add(cb.equal(membership.get("role"), TenantRole.MANAGER));
                predicates.add(cb.equal(membership.get("status"), MembershipStatus.ACTIVE));

                if (hasManagerName) {
                    predicates.add(
                            cb.like(
                                    cb.lower(
                                            cb.concat(
                                                    cb.concat(user.get("firstName"), " "),
                                                    user.get("lastName")
                                            )
                                    ),
                                    "%" + filter.managerName().toLowerCase(Locale.ROOT) + "%"
                            )
                    );
                }

                if (hasManagerEmail) {
                    predicates.add(
                            cb.like(
                                    cb.lower(user.get("email")),
                                    "%" + filter.managerEmail().toLowerCase(Locale.ROOT) + "%"
                            )
                    );
                }
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}