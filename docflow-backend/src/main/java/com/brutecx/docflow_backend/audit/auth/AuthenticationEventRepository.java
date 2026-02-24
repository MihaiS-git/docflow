package com.brutecx.docflow_backend.audit.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface AuthenticationEventRepository
        extends JpaRepository<AuthenticationEvent, UUID>,
        JpaSpecificationExecutor<AuthenticationEvent> {

    @Query("select min(e.timestamp) from AuthenticationEvent e")
    Instant findEarliestTimestamp();
}
