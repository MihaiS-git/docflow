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

    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);

    long countByTenantIdAndRoleAndStatus(
            UUID tenantId,
            TenantRole role,
            MembershipStatus status
    );

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

    @Query("""
            select m
            from UserTenantMembership m
            join fetch m.user u
            where m.tenant.id = :tenantId
            """)
    Page<UserTenantMembership> findByTenantIdWithUser(
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );

    @Query("""
            select m
            from UserTenantMembership m
            join fetch m.user u
            where m.tenant.id = :tenantId
            and (:role is null or m.role = :role)
            and (:status is null or m.status = :status)
            """)
    Page<UserTenantMembership> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("role") TenantRole role,
            @Param("status") MembershipStatus status,
            Pageable pageable
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
}
