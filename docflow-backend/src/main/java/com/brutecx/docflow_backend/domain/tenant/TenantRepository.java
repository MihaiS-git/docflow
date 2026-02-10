package com.brutecx.docflow_backend.domain.tenant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Query("select t.status from Tenant t where t.id = :id")
    Optional<TenantStatus> findStatusById(@Param("id") UUID id);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

}
