package com.brutecx.docflow_backend.domain.tenant;

import com.brutecx.docflow_backend.api.dto.admin.tenant.TenantListItemDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    @Query("select t.status from Tenant t where t.id = :id")
    Optional<TenantStatus> findStatusById(@Param("id") UUID id);

    Optional<Tenant> findFirstByTenantType(TenantType tenantType);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("""
            SELECT new com.brutecx.docflow_backend.api.dto.admin.tenant.TenantListItemDTO(
                t.id,
                t.name,
                t.description,
                t.owner.id,
                t.status,

                CONCAT(u.firstName,' ',u.lastName),
                u.email,

                COUNT(m2.id),

                t.dataRegion,
                t.retentionDays,

                MAX(m.updatedAt),

                t.createdAt,
                t.updatedAt
            )
            FROM Tenant t
            LEFT JOIN UserTenantMembership m
                   ON m.tenant.id = t.id
                   AND m.role = com.brutecx.docflow_backend.domain.tenant.TenantRole.MANAGER
                   AND m.status = com.brutecx.docflow_backend.domain.tenant.MembershipStatus.ACTIVE
            LEFT JOIN m.user u
            LEFT JOIN UserTenantMembership m2
                   ON m2.tenant.id = t.id
                   AND m2.status = com.brutecx.docflow_backend.domain.tenant.MembershipStatus.ACTIVE
            WHERE t.id IN :ids
            GROUP BY
                t.id,
                t.name,
                t.description,
                t.owner.id,
                t.status,
                u.firstName,
                u.lastName,
                u.email,
                t.dataRegion,
                t.retentionDays,
                t.createdAt,
                t.updatedAt
            """)
    List<TenantListItemDTO> fetchAdminRows(@Param("ids") List<UUID> ids);

    @Query("""
        select t
        from Tenant t
        left join fetch t.owner
        where t.id = :tenantId
    """)
    Optional<Tenant> findByIdWithOwner(@Param("tenantId") UUID tenantId);
}