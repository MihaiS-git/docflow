package com.brutecx.docflow_backend.domain.security.auditSigningKeys.rotation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AuditSigningKeyRotationLockRepository extends JpaRepository<AuditSigningKeyRotationLock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from AuditSigningKeyRotationLock l where l.id = 1")
    AuditSigningKeyRotationLock lockRow();
}