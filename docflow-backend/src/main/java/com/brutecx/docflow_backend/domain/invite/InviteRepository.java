package com.brutecx.docflow_backend.domain.invite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID>, JpaSpecificationExecutor<Invite> {

    // ---------- Locking variants ----------
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.token = :token")
    Optional<Invite> findByTokenForUpdate(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Invite> findByTenantIdAndStatusAndExpiresAtBefore(
            UUID tenantId,
            InviteStatus status,
            Instant expiresAt
    );
}