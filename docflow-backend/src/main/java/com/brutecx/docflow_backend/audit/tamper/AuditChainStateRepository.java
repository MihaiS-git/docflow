package com.brutecx.docflow_backend.audit.tamper;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AuditChainStateRepository extends JpaRepository<AuditChainState, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuditChainState s where s.stateKey = :stateKey")
    Optional<AuditChainState> findForUpdate(@Param("stateKey") String stateKey);
}
