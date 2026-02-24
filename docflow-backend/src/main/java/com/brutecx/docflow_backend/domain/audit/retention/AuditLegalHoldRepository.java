package com.brutecx.docflow_backend.domain.audit.retention;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLegalHoldRepository extends JpaRepository<AuditLegalHold, UUID>,
        JpaSpecificationExecutor<AuditLegalHold> {

    List<AuditLegalHold> findByStreamNameAndActiveIsTrue(String streamName);

    @Query("""
            SELECT h
            FROM AuditLegalHold h
            WHERE (:streamName IS NULL OR UPPER(h.streamName) = UPPER(:streamName))
              AND (:active IS NULL OR h.active = :active)
              AND (:caseReferenceId IS NULL OR h.caseReferenceId = :caseReferenceId)
              AND (:correlationId IS NULL OR h.correlationId = :correlationId)
              AND (:eventId IS NULL OR h.eventId = :eventId)
            """)
    Page<AuditLegalHold> query(
            @Param("streamName") String streamName,
            @Param("active") Boolean active,
            @Param("caseReferenceId") String caseReferenceId,
            @Param("correlationId") String correlationId,
            @Param("eventId") UUID eventId,
            Pageable pageable
    );
}