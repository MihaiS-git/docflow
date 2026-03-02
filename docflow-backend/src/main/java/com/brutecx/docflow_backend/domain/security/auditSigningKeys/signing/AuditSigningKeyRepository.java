package com.brutecx.docflow_backend.domain.security.auditSigningKeys.signing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditSigningKeyRepository extends JpaRepository<AuditSigningKey, String> {

    Optional<AuditSigningKey> findFirstByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from AuditSigningKey k where k.active = true")
    Optional<AuditSigningKey> findActiveForUpdate();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AuditSigningKey k set k.active = false where k.active = true")
    int deactivateAll();

    @Query(value = """
            select coalesce(max(cast(substring(key_id from length(:prefix) + 1) as bigint)), 0)
            from audit_signing_keys
            where key_id like concat(:prefix, '%')
            """, nativeQuery = true)
    long findMaxKeyVersion(@Param("prefix") String prefix);

    boolean existsByFingerprintSha256Hex(String fingerprintSha256Hex);
}