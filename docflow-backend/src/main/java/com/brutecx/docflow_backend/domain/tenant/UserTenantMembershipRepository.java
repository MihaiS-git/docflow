package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantLookupDTO;
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
            order by m.id
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
                    join fetch m.user
                    where m.tenant.id = :tenantId
                      and (:role is null or m.role = :role)
                      and (:status is null or m.status = :status)
                    """,
            countQuery = """
                    select count(m)
                    from UserTenantMembership m
                    join m.user u
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






    // 1. No filters
    @Query("""
    select m
    from UserTenantMembership m
    join fetch m.user
    where m.tenant.id = :tenantId
""")
    Page<UserTenantMembership> findByTenantIdWithUser(
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );

    // 2. Role only
    @Query("""
    select m
    from UserTenantMembership m
    join fetch m.user
    where m.tenant.id = :tenantId
      and m.role = :role
""")
    Page<UserTenantMembership> findByTenantIdAndRoleWithUser(
            @Param("tenantId") UUID tenantId,
            @Param("role") TenantRole role,
            Pageable pageable
    );

    // 3. Status only
    @Query("""
    select m
    from UserTenantMembership m
    join fetch m.user
    where m.tenant.id = :tenantId
      and m.status = :status
""")
    Page<UserTenantMembership> findByTenantIdAndStatusWithUser(
            @Param("tenantId") UUID tenantId,
            @Param("status") MembershipStatus status,
            Pageable pageable
    );

    // 4. Role + Status
    @Query("""
    select m
    from UserTenantMembership m
    join fetch m.user
    where m.tenant.id = :tenantId
      and m.role = :role
      and m.status = :status
""")
    Page<UserTenantMembership> findByTenantIdAndRoleAndStatusWithUser(
            @Param("tenantId") UUID tenantId,
            @Param("role") TenantRole role,
            @Param("status") MembershipStatus status,
            Pageable pageable
    );

    @Query("""
                select new com.brutecx.docflow_backend.api.dto.admin.tenant.TenantLookupDTO(
                    t.id,
                    t.name,
                    t.status
                )
                from UserTenantMembership m
                join m.tenant t
                where m.user.id = :userId
                  and m.role = :role
                  and m.status = :membershipStatus
                  and t.status = :tenantStatus
                order by t.name asc
            """)
    List<TenantLookupDTO> findActiveManagedTenantLookup(
            @Param("userId") UUID userId,
            @Param("role") TenantRole role,
            @Param("membershipStatus") MembershipStatus membershipStatus,
            @Param("tenantStatus") TenantStatus tenantStatus
    );

    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);
}
