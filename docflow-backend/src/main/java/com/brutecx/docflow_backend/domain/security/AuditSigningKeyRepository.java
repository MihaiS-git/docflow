package com.brutecx.docflow_backend.domain.security;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AuditSigningKeyRepository extends JpaRepository<AuditSigningKey, String> {

    Optional<AuditSigningKey> findFirstByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from AuditSigningKey k where k.active = true")
    Optional<AuditSigningKey> findActiveForUpdate();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditSigningKey k set k.active = false where k.active = true")
    int deactivateAll();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
                update AuditSigningKey k
                set k.expiresAt = :expiresAt
                where k.keyId = :keyId
            """)
    void forceExpire(String keyId, Instant expiresAt);
}