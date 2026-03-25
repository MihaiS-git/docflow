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

    // ---------- Lock for token consumption ----------
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.hashedToken = :token")
    Optional<Invite> findByTokenForUpdate(@Param("token") String token);

    // ---------- Read-only lookup ----------
    Optional<Invite> findByHashedToken(String hashedToken);

    // ---------- Cleanup locking ----------
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Invite> findByTenantIdAndStatusAndExpiresAtBefore(
            UUID tenantId,
            InviteStatus status,
            Instant expiresAt
    );

    // ---------- Revoke locking ----------
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invite i where i.id = :id")
    Optional<Invite> findByIdForUpdate(@Param("id") UUID id);
}