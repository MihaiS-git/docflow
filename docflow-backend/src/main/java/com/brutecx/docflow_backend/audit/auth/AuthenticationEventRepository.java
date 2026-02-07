package com.brutecx.docflow_backend.audit.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AuthenticationEventRepository extends JpaRepository<AuthenticationEvent, UUID> {
    Page<AuthenticationEvent> findByCorrelationId(String correlationId, Pageable pageable);

    Page<AuthenticationEvent> findByTimestampBetween(Instant from, Instant to, Pageable pageable);

}
