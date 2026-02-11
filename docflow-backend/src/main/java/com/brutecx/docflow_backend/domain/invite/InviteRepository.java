package com.brutecx.docflow_backend.domain.invite;

import com.brutecx.docflow_backend.api.dto.invite.InviteAdminViewDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
    Optional<Invite> findByToken(String token);

    List<Invite> findByTenantIdAndStatusAndExpiresAtBefore(
            UUID tenantId,
            InviteStatus status,
            Instant expiresAt
    );

    @Query("""
            select new com.brutecx.docflow_backend.api.dto.invite.InviteAdminViewDTO(
                i.id,
                i.email,
                i.status,
                i.timestamp,
                i.expiresAt,
                0,
                t.id,
                t.name
            )
            from Invite i
            join Tenant t on t.id = i.tenantId
            """)
    Page<InviteAdminViewDTO> findAllWithTenant(Pageable pageable);


}
