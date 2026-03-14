package com.brutecx.docflow_backend.domain.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTenantMembershipRepository extends JpaRepository<UserTenantMembership, UUID> {

    Optional<UserTenantMembership> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    /**
     * Concurrency-safe "last MANAGER" enforcement helper.
     * Locks all ACTIVE MANAGER memberships for the tenant in the current transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m.id
            from UserTenantMembership m
            where m.tenant.id = :tenantId
              and m.role = :role
              and m.status = :status
            """)
    List<UUID> lockActiveManagers(
            @Param("tenantId") UUID tenantId,
            @Param("role") TenantRole role,
            @Param("status") MembershipStatus status
    );

    @Query(
            value = """
                    select m
                    from UserTenantMembership m
                    join m.user u
                    where m.tenant.id = :tenantId
                      and (:role is null or m.role = :role)
                      and (:status is null or m.status = :status)
                    """,
            countQuery = """
                    select count(m)
                    from UserTenantMembership m
                    where m.tenant.id = :tenantId
                      and (:role is null or m.role = :role)
                      and (:status is null or m.status = :status)
                    """
    )
    Page<UserTenantMembership> findFilteredWithUser(
            @Param("tenantId") UUID tenantId,
            @Param("role") TenantRole role,
            @Param("status") MembershipStatus status,
            Pageable pageable
    );

    @Query("""
    select distinct m.tenant
    from UserTenantMembership m
    where m.user.id = :userId
      and m.role = :role
      and m.status = :status
    order by m.tenant.createdAt desc
""")
    List<Tenant> findTenantsByUserRole(
            @Param("userId") UUID userId,
            @Param("role") TenantRole role,
            @Param("status") MembershipStatus status
    );


    boolean existsByUserIdAndTenantId(UUID id, UUID id1);

    long countByTenantIdAndStatus(
            UUID tenantId,
            MembershipStatus status
    );

    @Query("""
        select u.firstName, u.lastName, u.email
        from UserTenantMembership m
        join m.user u
        where m.tenant.id = :tenantId
          and m.role = com.brutecx.docflow_backend.domain.tenant.TenantRole.MANAGER
          and m.status = com.brutecx.docflow_backend.domain.tenant.MembershipStatus.ACTIVE
        """)
    List<Object[]> findActiveManagers(UUID tenantId);
}
